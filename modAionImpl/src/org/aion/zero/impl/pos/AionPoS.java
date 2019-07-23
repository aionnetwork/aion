package org.aion.zero.impl.pos;

import static org.aion.mcf.core.ImportResult.IMPORTED_BEST;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.base.AionTransaction;
import org.aion.crypto.HashUtil;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventConsensus.CALLBACK;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.core.ImportResult;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.types.StakedBlockHeader;
import org.slf4j.Logger;

public class AionPoS {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private static final int syncLimit = 128;

    protected IAionBlockchain blockchain;
    protected IPendingState pendingState;
    private IEventMgr eventMgr;

    private AtomicBoolean initialized = new AtomicBoolean(false);
    private AtomicBoolean newPendingTxReceived = new AtomicBoolean(false);
    private AtomicLong lastUpdate = new AtomicLong(0);
    private AtomicLong delta = new AtomicLong(100);

    private AtomicBoolean shutDown = new AtomicBoolean();
    private SyncMgr syncMgr;

    private EventExecuteService ees;

    private byte[] seed;

    private final class EpPOS implements Runnable {
        boolean go = true;

        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();

                if (e.getEventType() == IHandler.TYPE.TX0.getValue()
                        && e.getCallbackType() == EventTx.CALLBACK.PENDINGTXRECEIVED0.getValue()) {
                    newPendingTxReceived.set(true);
                } else if (e.getEventType() == IHandler.TYPE.CONSENSUS.getValue()
                        && e.getCallbackType() == CALLBACK.ON_STAKE_SIG.getValue()) {
                    finalizeBlock((StakingBlock) e.getFuncArgs().get(0));
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    private final CfgAion config = CfgAion.inst();

    /**
     * Creates an {@link AionPoS} instance. Be sure to call {@link #init(IAionBlockchain,
     * IPendingState, IEventMgr)} to initialize the instance.
     */
    public AionPoS() {}

    /**
     * Initializes this instance.
     *
     * @param blockchain Aion blockchain instance
     * @param pendingState List of Aion transactions
     * @param eventMgr Event manager
     */
    public void init(IAionBlockchain blockchain, IPendingState pendingState, IEventMgr eventMgr) {
        if (initialized.compareAndSet(false, true)) {
            this.blockchain = blockchain;
            this.pendingState = pendingState;
            this.eventMgr = eventMgr;
            this.syncMgr = SyncMgr.inst();

            // return early if staking is disabled, otherwise we are doing needless
            // work by generating new block templates on IMPORT_BEST
            if (!config.getConsensus().getStaking()) {
                return;
            }

            setupHandler();
            ees = new EventExecuteService(10_000, "EpPos", Thread.NORM_PRIORITY, LOG);
            ees.setFilter(setEvtFilter());

            registerCallback();
            ees.start(new EpPOS());

            new Thread(
                            () -> {
                                while (!shutDown.get()) {
                                    try {
                                        Thread.sleep(100);

                                        long now = System.currentTimeMillis();

                                        if (((now - lastUpdate.get()) >= delta.get())
                                                && blockchain
                                                        .getStakingContractHelper()
                                                        .isContractDeployed()) {
                                            seed =
                                                    ChainConfiguration.getStakerKey()
                                                            .sign(
                                                                    (blockchain
                                                                                    .getBestStakingBlock())
                                                                            .getSeed())
                                                            .getSignature();

                                            StakingBlock newBlock = createNewBlockTemplate(seed);

                                            long votes =
                                                    blockchain
                                                            .getStakingContractHelper()
                                                            .callGetVote(
                                                                    AddressUtils.wrapAddress(
                                                                            config.getConsensus()
                                                                                    .getStakerAddress()));

                                            // TODO: [unity] might change the threshold.
                                            if (votes < 1) {
                                                continue;
                                            }

                                            double newDelta =
                                                    newBlock.getDifficultyBI().doubleValue()
                                                            * Math.log(
                                                                    BigInteger.TWO
                                                                            .pow(256)
                                                                            .divide(
                                                                                    new BigInteger(
                                                                                            1,
                                                                                            HashUtil
                                                                                                    .h256(
                                                                                                            seed)))
                                                                            .doubleValue())
                                                            / votes;

                                            delta.set(Math.max((long) (newDelta * 1000), 100));
                                        }
                                    } catch (InterruptedException e) {
                                        break;
                                    }
                                }
                            },
                            "pos")
                    .start();
        }
    }

    /** Sets up the consensus event handler. */
    private void setupHandler() {
        List<IEvent> txEvts = new ArrayList<>();
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0));
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0));
        txEvts.add(new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0));
        eventMgr.registerEvent(txEvts);

        List<IEvent> events = new ArrayList<>();
        events.add(new EventConsensus(EventConsensus.CALLBACK.ON_STAKING_BLOCK_TEMPLATE));
        events.add(new EventConsensus(CALLBACK.ON_STAKE_SIG));
        eventMgr.registerEvent(events);
    }

    private Set<Integer> setEvtFilter() {
        Set<Integer> eventSN = new HashSet<>();
        int sn = IHandler.TYPE.TX0.getValue() << 8;
        eventSN.add(sn + EventTx.CALLBACK.PENDINGTXRECEIVED0.getValue());

        sn = IHandler.TYPE.CONSENSUS.getValue() << 8;
        eventSN.add(sn + EventConsensus.CALLBACK.ON_STAKE_SIG.getValue());

        //TODO : [unity] check do we really need this event?

        //        sn = IHandler.TYPE.BLOCK0.getValue() << 8;
        //        eventSN.add(sn + EventBlock.CALLBACK.ONBEST0.getValue());

        return eventSN;
    }

    /**
     * Registers callback for the {@link
     * org.aion.evtmgr.impl.evt.EventConsensus.CALLBACK#ON_SOLUTION} event.
     */
    private void registerCallback() {
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
     * @param signedBlock the block has been signed.
     */
    private void finalizeBlock(StakingBlock signedBlock) {
        if (!shutDown.get()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Best block num [{}]", blockchain.getBestBlock().getNumber());
                LOG.debug(
                        "Best block hash [{}]",
                        Hex.toHexString(blockchain.getBestBlock().getHash()));
            }

            // This can be improved
            ImportResult importResult = AionImpl.inst().addNewMinedBlock(signedBlock);

            // Check that the new block was successfully added
            if (importResult.isSuccessful()) {
                if (importResult == IMPORTED_BEST) {
                    LOG.info(
                            "Staking block sealed <num={}, hash={}, diff={}, tx={}>",
                            signedBlock.getNumber(),
                            signedBlock.getShortHash(),
                            signedBlock.getHeader().getDifficultyBI().toString(),
                            signedBlock.getTransactionsList().size());
                } else {
                    LOG.info(
                            "Staking block sealed <num={}, hash={}, diff={}, td={}, tx={}, result={}>",
                            signedBlock.getNumber(),
                            signedBlock.getShortHash(),
                            signedBlock.getHeader().getDifficultyBI().toString(),
                            blockchain.getTotalDifficulty(),
                            signedBlock.getTransactionsList().size(),
                            importResult);
                }
                // TODO: fire block mined event
            } else {
                LOG.debug(
                        "Unable to import a new mined block; restarting mining.\n"
                                + "Mined block import result is "
                                + importResult
                                + " : "
                                + signedBlock.getShortHash());
            }
        }
    }

    /** Creates a new block template. */
    private StakingBlock createNewBlockTemplate(byte[] seed) {

        if (!shutDown.get()) {
            // TODO: Validate the trustworthiness of getNetworkBestBlock - can
            // it be used in DDOS?
            if (this.syncMgr.getNetworkBestBlockNumber() - blockchain.getBestBlock().getNumber()
                    > syncLimit) {
                return null;
            }

            if (seed == null || seed.length != StakedBlockHeader.SEED_LENGTH) {
                LOG.error("Invalid seed info.");
                return null;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new block template");
            }

            Block bestBlock = blockchain.getBestBlock();

            List<AionTransaction> txs = pendingState.getPendingTransactions();

            StakingBlock newBlock = (StakingBlock) blockchain.createNewBlock(bestBlock, txs, false, seed);

            EventConsensus ev = new EventConsensus(EventConsensus.CALLBACK.ON_STAKING_BLOCK_TEMPLATE);
            ev.setFuncArgs(Collections.singletonList(newBlock));
            eventMgr.newEvent(ev);

            // update last timestamp
            lastUpdate.set(System.currentTimeMillis());
            return newBlock;
        }
        return null;
    }

    public void shutdown() {
        if (ees != null) {
            ees.shutdown();
        }
        shutDown.set(true);
    }
}
