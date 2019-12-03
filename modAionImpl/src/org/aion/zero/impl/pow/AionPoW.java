package org.aion.zero.impl.pow;

import org.aion.mcf.blockchain.BlockHeader;
import static org.aion.zero.impl.core.ImportResult.IMPORTED_BEST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.base.AionTransaction;
import org.aion.equihash.AionPowSolution;
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
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.pendingState.IPendingState;
import org.aion.zero.impl.core.ImportResult;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.blockchain.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * {@link AionPoW} contains the logic to process new mined blocks and dispatch new mining task to
 * miners when needed.
 */
public class AionPoW {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private static final int syncLimit = 128;

    protected IAionBlockchain blockchain;
    protected IPendingState pendingState;
    protected IEventMgr eventMgr;

    protected AtomicBoolean initialized = new AtomicBoolean(false);
    protected AtomicBoolean newPendingTxReceived = new AtomicBoolean(false);
    // This value is the time of the last "full update" of the block template, that is, the last time
    // we created a fresh block from createNewBlockTemplate()
    protected AtomicLong lastUpdate = new AtomicLong(0);
    private AionBlock latestBlockTemplate;

    private AtomicBoolean shutDown = new AtomicBoolean();
    private SyncMgr syncMgr;

    private EventExecuteService ees;

    private final class EpPOW implements Runnable {
        boolean go = true;

        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();

                if (e.getEventType() == IHandler.TYPE.TX0.getValue()
                        && e.getCallbackType() == EventTx.CALLBACK.PENDINGTXRECEIVED0.getValue()) {
                    newPendingTxReceived.set(true);
                } else if (e.getEventType() == IHandler.TYPE.BLOCK0.getValue()
                        && e.getCallbackType() == EventBlock.CALLBACK.ONBEST0.getValue()) {
                    // create a new block template every time the best block
                    // updates.
                    createNewBlockTemplate();
                } else if (e.getEventType() == IHandler.TYPE.CONSENSUS.getValue()
                        && e.getCallbackType() == EventConsensus.CALLBACK.ON_SOLUTION.getValue()) {
                    processSolution((AionPowSolution) e.getFuncArgs().get(0));
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    private final CfgAion config = CfgAion.inst();

    /**
     * Creates an {@link AionPoW} instance. Be sure to call {@link #init(IAionBlockchain,
     * IPendingState, IEventMgr, SyncMgr)} to initialize the instance.
     */
    public AionPoW() {}

    /**
     * Initializes this instance.
     *
     * @param blockchain Aion blockchain instance
     * @param pendingState List of Aion transactions
     * @param eventMgr Event manager
     */
    public void init(IAionBlockchain blockchain, IPendingState pendingState, IEventMgr eventMgr, SyncMgr syncMgr) {
        if (initialized.compareAndSet(false, true)) {
            this.blockchain = blockchain;
            this.pendingState = pendingState;
            this.eventMgr = eventMgr;
            this.syncMgr = syncMgr;

            // return early if mining is disabled, otherwise we are doing needless
            // work by generating new block templates on IMPORT_BEST
            if (!config.getConsensus().getMining()) return;

            setupHandler();
            ees = new EventExecuteService(100_000, "EpPow", Thread.NORM_PRIORITY, LOG);
            ees.setFilter(setEvtFilter());

            registerCallback();
            ees.start(new EpPOW());

            new Thread(
                            () -> {
                                while (!shutDown.get()) {
                                    try {
                                        Thread.sleep(100);

                                        long now = System.currentTimeMillis();
                                        if ((now - lastUpdate.get() > 3000 && newPendingTxReceived.compareAndSet(true, false))
                                                || now - lastUpdate.get() > 10000) {
                                            createNewBlockTemplate();
                                        } else {
                                            updateTimestamp(TimeUnit.MILLISECONDS.toSeconds(now));
                                        }
                                    } catch (InterruptedException e) {
                                        break;
                                    }
                                }
                            },
                            "pow")
                    .start();
        }
    }

    /** Sets up the consensus event handler. */
    private void setupHandler() {
        List<IEvent> txEvts = new ArrayList<>();
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0));
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0));
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
     * Registers callback for the {@link
     * org.aion.evtmgr.impl.evt.EventConsensus.CALLBACK#ON_SOLUTION} event.
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
     * @param solution The generated equihash solution
     */
    protected synchronized void processSolution(AionPowSolution solution) {
        if (!shutDown.get()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Best block num [{}]", blockchain.getBestBlock().getNumber());
                LOG.debug(
                        "Best block hash [{}]",
                        Hex.toHexString(blockchain.getBestBlock().getHash()));
            }

            AionBlock block = solution.getBlock();
            if (!Arrays.equals(block.getHeader().getNonce(), new byte[32])
                    && !(block.getHeader().getNonce().length == 0)) {
                // block has been processed
                return;
            }

            // set the nonce and solution
            try {
                block.seal(solution.getNonce(), solution.getSolution());
            } catch (Exception e) {
                LOG.error("seal block failed!", e);
                return;
            }

            // This can be improved
            ImportResult importResult = AionImpl.inst().addNewBlock(block);

            // Check that the new block was successfully added
            if (importResult.isSuccessful()) {
                if (importResult == IMPORTED_BEST) {
                    LOG.info(
                            "block sealed <num={}, hash={}, diff={}, tx={}>",
                            block.getNumber(),
                            block.getShortHash(),
                            block.getHeader().getDifficultyBI().toString(),
                            block.getTransactionsList().size());
                } else {
                    LOG.debug(
                            "block sealed <num={}, hash={}, diff={}, td={}, tx={}, result={}>",
                            block.getNumber(),
                            block.getShortHash(),
                            block.getHeader().getDifficultyBI().toString(),
                            blockchain.getTotalDifficulty(),
                            block.getTransactionsList().size(),
                            importResult);
                }
                // TODO: fire block mined event
            } else {
                LOG.info(
                        "Unable to import a new mined block; restarting mining.\n"
                                + "Mined block import result is "
                                + importResult
                                + " : "
                                + block.getShortHash());
            }
        }
    }

    /** Creates a new block template. */
    protected synchronized void createNewBlockTemplate() {
        if (!shutDown.get()) {
            // TODO: Validate the trustworthiness of getNetworkBestBlock - can
            // it be used in DDOS?
            if (this.syncMgr.getNetworkBestBlockNumber() - blockchain.getBestBlock().getNumber()
                    > syncLimit) {
                return;
            }

            Block bestBlock =
                    blockchain.getBlockByNumber(blockchain.getBestBlock().getNumber());
            
            if (blockchain.isUnityForkEnabledAtNextBlock() && bestBlock.getHeader().getSealType() == BlockHeader.BlockSealType.SEAL_POW_BLOCK) {
                return;
            } else {

                LOG.debug("Internal miner creating a new mining block template");

                List<AionTransaction> txs = pendingState.getPendingTransactions();

                AionBlock newBlock;
                try {
                    newBlock = blockchain.createNewMiningBlock(bestBlock, txs, false);
                } catch (Exception e) {
                    LOG.error("Create new block failed!", e);
                    return;
                }

                if (newBlock == null) {
                    return;
                }

                EventConsensus ev = new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);
                ev.setFuncArgs(Collections.singletonList(newBlock));
                eventMgr.newEvent(ev);

                lastUpdate.set(System.currentTimeMillis());
                latestBlockTemplate = newBlock;
            }
        }
    }

    /** Creates a new block template. */
    protected synchronized void updateTimestamp(long systemTime) {
        if (!shutDown.get() && systemTime > latestBlockTemplate.getTimestamp()) {
            A0BlockHeader newHeader = latestBlockTemplate.getHeader().updateTimestamp(systemTime);
            AionBlock newBlock = new AionBlock(newHeader, latestBlockTemplate.getTransactionsList());
            
            EventConsensus ev = new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);
            ev.setFuncArgs(Collections.singletonList(newBlock));
            eventMgr.newEvent(ev);

            latestBlockTemplate = newBlock;
        }
    }

    public synchronized void shutdown() {
        if (ees != null) {
            ees.shutdown();
        }
        shutDown.set(true);
    }
}
