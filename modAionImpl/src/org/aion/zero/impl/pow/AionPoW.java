/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.pow;

import org.aion.base.util.Hex;
import org.aion.equihash.Solution;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.aion.mcf.core.ImportResult.IMPORTED_BEST;

/**
 * {@link AionPoW} contains the logic to process new mined blocks and dispatch
 * new mining task to miners when needed.
 */
public class AionPoW {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private static final int syncLimit = 128;

    protected IAionBlockchain blockchain;
    protected IPendingState<AionTransaction> pendingState;
    protected IEventMgr eventMgr;

    protected AtomicBoolean initialized = new AtomicBoolean(false);
    protected AtomicBoolean newPendingTxReceived = new AtomicBoolean(false);
    protected AtomicLong lastUpdate = new AtomicLong(0);

    private AtomicBoolean shutDown = new AtomicBoolean();
    private SyncMgr syncMgr;

    private EventExecuteService ees;

    private final class EpPOW implements Runnable {
        boolean go = true;
        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();

                if (e.getEventType() == IHandler.TYPE.TX0.getValue() && e.getCallbackType() == EventTx.CALLBACK.PENDINGTXRECEIVED0.getValue()) {
                    newPendingTxReceived.set(true);
                } else if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue() && e.getCallbackType() == EventBlock.CALLBACK.ONBEST0.getValue()) {
                    // create a new block template every time the best block
                    // updates.
                    createNewBlockTemplate();
                } else if (e.getEventType() == IHandler.TYPE.CONSENSUS.getValue() && e.getCallbackType() == EventConsensus.CALLBACK.ON_SOLUTION.getValue()) {
                    processSolution((Solution) e.getFuncArgs().get(0));
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()){
                    go = false;
                }
            }
        }
    }

    private final CfgAion config = CfgAion.inst();

    /**
     * Creates an {@link AionPoW} instance. Be sure to call
     * {@link #init(IAionBlockchain, IPendingState, IEventMgr)} to initialize
     * the instance.
     */
    public AionPoW() {
    }

    /**
     * Initializes this instance.
     *
     * @param blockchain
     *            Aion blockchain instance
     * @param pendingState
     *            List of Aion transactions
     * @param eventMgr
     *            Event manager
     */
    public void init(IAionBlockchain blockchain, IPendingState<AionTransaction> pendingState, IEventMgr eventMgr) {
        if (initialized.compareAndSet(false, true)) {
            this.blockchain = blockchain;
            this.pendingState = pendingState;
            this.eventMgr = eventMgr;
            this.syncMgr = SyncMgr.inst();


            // return early if mining is disabled, otherwise we are doing needless
            // work by generating new block templates on IMPORT_BEST
            if (!config.getConsensus().getMining())
                return;

            setupHandler();
            ees = new EventExecuteService(100_000, "EpPow", Thread.NORM_PRIORITY, LOG);
            ees.setFilter(setEvtFilter());


            registerCallback();
            ees.start(new EpPOW());

            new Thread(() -> {
                while (!shutDown.get()) {
                    try {
                        Thread.sleep(100);

                        long now = System.currentTimeMillis();
                        if (now - lastUpdate.get() > 3000 && newPendingTxReceived.compareAndSet(true, false)
                                || now - lastUpdate.get() > 10000) { // fallback, when
                                                               // we never
                                                               // received any
                                                               // events
                            createNewBlockTemplate();
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "pow").start();
        }
    }

    /**
     * Sets up the consensus event handler.
     */
    private void setupHandler() {
        List<IEvent> txEvts = new ArrayList<>();
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0));
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0));
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0));
        eventMgr.registerEvent(txEvts);

        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_SOLUTION));
        eventMgr.registerEvent(events);
    }

    private Set<Integer> setEvtFilter() {
        Set<Integer> eventSN = new HashSet<>();
        int sn = IHandler.TYPE.TX0.getValue() << 8;
        eventSN.add(sn + EventTx.CALLBACK.PENDINGTXRECEIVED0.getValue());

        sn = IHandler.TYPE.CONSENSUS.getValue() << 8;
        eventSN.add(sn + EventConsensus.CALLBACK.ON_SOLUTION.getValue());

        sn = IHandler.TYPE.BLOCK0.getValue() << 8;
        eventSN.add(sn + EventBlock.CALLBACK.ONBEST0.getValue());

        return eventSN;
    }

    /**
     * Registers callback for the
     * {@link org.aion.evtmgr.impl.evt.EventConsensus.CALLBACK#ON_SOLUTION}
     * event.
     */
    public void registerCallback() {
        IHandler consensusHandler = eventMgr.getHandler(IHandler.TYPE.CONSENSUS.getValue());
        consensusHandler.eventCallback(new EventCallback(ees, LOG));

        IHandler blockHandler = eventMgr.getHandler(IHandler.TYPE.BLOCK0.getValue());
        blockHandler.eventCallback(new EventCallback(ees, LOG));

        IHandler transactionHandler = eventMgr.getHandler(IHandler.TYPE.TX0.getValue());
        transactionHandler.eventCallback(new EventCallback(ees, LOG));
    }

    /**
     * Processes a received solution.
     *
     * @param solution
     *            The generated equihash solution
     */
    protected synchronized void processSolution(Solution solution) {
        if (!shutDown.get()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Best block num [{}]", blockchain.getBestBlock().getNumber());
                LOG.debug("Best block nonce [{}]", Hex.toHexString(blockchain.getBestBlock().getNonce()));
                LOG.debug("Best block hash [{}]", Hex.toHexString(blockchain.getBestBlock().getHash()));
            }

            AionBlock block = (AionBlock) solution.getBlock();
            if (!Arrays.equals(block.getHeader().getNonce(), new byte[32])) {
                // block has been processed
                return;
            }

            // set the nonce and solution
            block.getHeader().setNonce(solution.getNonce());
            block.getHeader().setSolution(solution.getSolution());

            // This can be improved
            ImportResult importResult = AionImpl.inst().addNewMinedBlock(block);

            // Check that the new block was successfully added
            if (importResult.isSuccessful()) {
                if (importResult == IMPORTED_BEST) {
                    LOG.info("block sealed <num={}, hash={}, diff={}, tx={}>", block.getNumber(), block.getShortHash(),
                            // LogUtil.toHexF8(newBlock.getHash()),
                            block.getHeader().getDifficultyBI().toString(), block.getTransactionsList().size());
                } else {
                    LOG.debug("block sealed <num={}, hash={}, diff={}, tx={}, result={}>", block.getNumber(),
                            block.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
                            block.getHeader().getDifficultyBI().toString(), block.getTransactionsList().size(),
                            importResult);
                }
                // TODO: fire block mined event
            } else {
                LOG.info("Unable to import a new mined block; restarting mining.\n" + "Mined block import result is "
                        + importResult + " : " + block.getShortHash());
            }
        }
    }

    /**
     * Creates a new block template.
     */
    protected synchronized void createNewBlockTemplate() {
        if (!shutDown.get()) {
            // TODO: Validate the trustworthiness of getNetworkBestBlock - can
            // it be used in DDOS?
            if (this.syncMgr.getNetworkBestBlockNumber() - blockchain.getBestBlock().getNumber() > syncLimit) {
                return;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new block template");
            }

            AionBlock bestBlock = blockchain.getBlockByNumber(blockchain.getBestBlock().getNumber());

            List<AionTransaction> txs = pendingState.getPendingTransactions();

            AionBlock newBlock = blockchain.createNewBlock(bestBlock, txs, false);

            EventConsensus ev = new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);
            ev.setFuncArgs(Collections.singletonList(newBlock));
            eventMgr.newEvent(ev);

            // update last timestamp
            lastUpdate.set(System.currentTimeMillis());
        }
    }

    public synchronized void shutdown() {
        if (ees != null) {
            ees.shutdown();
        }
        shutDown.set(true);
    }
}
