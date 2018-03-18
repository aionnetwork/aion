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

import org.aion.base.type.*;
import org.aion.base.util.Hex;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.core.ImportResult;
import org.aion.equihash.Solution;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

            setupHandler();
            registerCallback();

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
    public void setupHandler() {
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

    /**
     * Registers callback for the
     * {@link org.aion.evtmgr.impl.evt.EventConsensus.CALLBACK#ON_SOLUTION}
     * event.
     */
    public void registerCallback() {
        IHandler consensusHandler = eventMgr.getHandler(IHandler.TYPE.CONSENSUS.getValue());
        consensusHandler.eventCallback(
                new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                    @Override
                    public void onSolution(ISolution solution) {
                        processSolution((Solution) solution);
                    }
                });

        IHandler blockHandler = eventMgr.getHandler(IHandler.TYPE.BLOCK0.getValue());
        blockHandler.eventCallback(
                new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                    @Override
                    public void onBest(IBlock block, List<?> receipts) {
                        // create a new block template every time the best block
                        // updates.
                        createNewBlockTemplate();
                    }
                });

        IHandler transactionHandler = eventMgr.getHandler(IHandler.TYPE.TX0.getValue());
        transactionHandler.eventCallback(
                new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                    @Override
                    public void onPendingTxReceived(ITransaction tx) {
                        // set the transaction flag to true
                        newPendingTxReceived.set(true);
                    }
                });
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
            block.getHeader().setTimestamp(solution.getTimeStamp());

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

            if (!config.getConsensus().getMining()) {
                return;
            }

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
        shutDown.set(true);
    }
}
