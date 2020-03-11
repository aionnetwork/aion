package org.aion.zero.impl.blockchain;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypeRule;
import org.aion.evtmgr.EventMgrModule;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionImpl.NetworkBestBlockCallback;
import org.aion.zero.impl.blockchain.AionImpl.PendingTxCallback;
import org.aion.zero.impl.blockchain.AionImpl.TransactionBroadcastCallback;
import org.aion.zero.impl.config.CfgNetP2p;
import org.aion.mcf.db.Repository;
import org.aion.p2p.Handler;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.pendingState.AionPendingStateImpl;
import org.aion.zero.impl.pendingState.IPendingState;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.impl.sync.NodeWrapper;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.handler.BlockPropagationHandler;
import org.aion.zero.impl.sync.handler.BroadcastNewBlockHandler;
import org.aion.zero.impl.sync.handler.BroadcastTxHandler;
import org.aion.zero.impl.sync.handler.ReqBlocksBodiesHandler;
import org.aion.zero.impl.sync.handler.ReqBlocksHeadersHandler;
import org.aion.zero.impl.sync.handler.ReqStatusHandler;
import org.aion.zero.impl.sync.handler.ResBlocksBodiesHandler;
import org.aion.zero.impl.sync.handler.ResBlocksHeadersHandler;
import org.aion.zero.impl.sync.handler.ResStatusHandler;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.StakingBlock;
import org.slf4j.Logger;

public class AionHub {

    private static final Logger genLOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    private static final Logger syncLOG = AionLoggerFactory.getLogger(LogEnum.SYNC.name());
    private static final Logger surveyLOG = AionLoggerFactory.getLogger(LogEnum.SURVEY.name());

    private IP2pMgr p2pMgr;
    private int chainId; // TODO: can be made final upon constructor refactoring

    private CfgAion cfg;

    private SyncMgr syncMgr;

    private BlockPropagationHandler propHandler;

    private AionPendingStateImpl mempool;

    private AionBlockchainImpl blockchain;

    private IEventMgr eventMgr;

    private AionPoW pow;

    private AtomicBoolean start = new AtomicBoolean(true);

    private static final byte apiVersion = 2;

    /** Test functionality for checking if the hub has been shut down. */
    public boolean isRunning() {
        return start.get();
    }

    /**
     * A "cached" block that represents our local best block when the application is first booted.
     */
    private volatile Block startingBlock;

    private ReentrantLock blockTemplateLock;

    public AionHub(PendingTxCallback pendingTxCallback, NetworkBestBlockCallback networkBestBlockCallback, TransactionBroadcastCallback transactionBroadcastCallback) {
        initializeHub(CfgAion.inst(), null, pendingTxCallback, networkBestBlockCallback,
            transactionBroadcastCallback, false);
    }

    private void initializeHub(
            CfgAion _cfgAion,
            AionBlockchainImpl _blockchain,
            PendingTxCallback pendingTxCallback,
            NetworkBestBlockCallback networkBestBlockCallback,
            TransactionBroadcastCallback transactionBroadcastCallback,
            boolean forTest) {

        this.cfg = _cfgAion;

        // load event manager before init blockchain instance
        loadEventMgr(forTest);
        registerBlockEvents();

        // the current unit tests require passing in a different repository instance
        // during normal execution we need to instantiate the repository
        // for this reason we pass in null when a new instance is required
        this.blockchain = _blockchain == null ? new AionBlockchainImpl(cfg, eventMgr, forTest) : _blockchain;

        try {
            this.blockchain.load(cfg.getGenesis(), genLOG);
        } catch (IllegalStateException e) {
            genLOG.error(
                    "Found database corruption, please re-import your database by using ./aion.sh -n <network> --redo-import",
                    e);
            System.exit(SystemExitCodes.DATABASE_CORRUPTION);
        }

        this.startingBlock = this.blockchain.getBestBlock();

        if (blockchain.forkUtility.is040ForkActive(blockchain.getBestBlock().getNumber())) {
            TransactionTypeRule.allowAVMContractTransaction();
        }

        this.mempool =
                new AionPendingStateImpl(
                        blockchain,
                        cfg.getConsensus().getEnergyStrategy().getUpperBound(),
                        cfg.getTx().getTxPendingTimeout(),
                        cfg.getTx().getPoolBackup(),
                        cfg.getTx().isSeedMode(),
                        cfg.getTx().getPoolDump(),
                        pendingTxCallback,
                        networkBestBlockCallback,
                        transactionBroadcastCallback,
                        forTest);

        if (cfg.getTx().isSeedMode()) {
            genLOG.info("Seed node mode enabled!");
        }

        /*
         * p2p hook up start sync mgr needs to be initialed after loadBlockchain()
         * method
         */
        CfgNetP2p cfgNetP2p = this.cfg.getNet().getP2p();
        this.chainId = this.cfg.getNet().getId();

        // there are two p2p implementation , now just point to impl1.
        this.p2pMgr =
                new P2pMgr(
                        AionLoggerFactory.getLogger(LogEnum.P2P.name()),
                        AionLoggerFactory.getLogger(LogEnum.SURVEY.name()),
                        this.chainId,
                        Version.KERNEL_VERSION,
                        this.cfg.getId(),
                        cfgNetP2p.getIp(),
                        cfgNetP2p.getPort(),
                        this.cfg.getNet().getNodes(),
                        cfgNetP2p.getDiscover(),
                        cfgNetP2p.getMaxTempNodes(),
                        cfgNetP2p.getMaxActiveNodes(),
                        cfgNetP2p.getBootlistSyncOnly(),
                        cfgNetP2p.getErrorTolerance());

        this.syncMgr = new SyncMgr(
                blockchain,
                p2pMgr,
                eventMgr,
                cfg.getSync().getShowStatus(),
                cfg.getSync().getShowStatistics(),
                cfg.getNet().getP2p().getMaxActiveNodes());

        ChainConfiguration chainConfig = new ChainConfiguration();
        this.propHandler =
                new BlockPropagationHandler(
                        1024,
                        this.blockchain,
                        syncMgr.getSyncStats(),
                        p2pMgr,
                        chainConfig.createBlockHeaderValidator(),
                        cfg.getNet().getP2p().inSyncOnlyMode(),
                        apiVersion,
                        mempool);

        registerCallback();

        if (!forTest) {
            p2pMgr.run();
        }

        if (!AionBlockchainImpl.enableFullSyncCheck) {
            this.pow = new AionPoW();
            this.pow.init(blockchain, mempool, eventMgr, syncMgr);
        }

        blockTemplateLock = new ReentrantLock();

        SelfNodeStatusCallback callback = new SelfNodeStatusCallback(p2pMgr);
        callback.updateBlockStatus(blockchain.getBestBlock().getNumber(), blockchain.getBestBlock().getHash(), blockchain.getTotalDifficulty());

        blockchain.setNodeStatusCallback(callback);
        blockchain.setBestBlockImportCallback(new BestBlockImportCallback(mempool));
    }

    public static AionHub createForTesting(
        CfgAion _cfgAion, AionBlockchainImpl _blockchain,
        PendingTxCallback pendingTxCallback, NetworkBestBlockCallback networkBestBlockCallback, TransactionBroadcastCallback transactionBroadcastCallback) {
        return new AionHub(_cfgAion, _blockchain, pendingTxCallback, networkBestBlockCallback,
            transactionBroadcastCallback, true);
    }

    private AionHub(
            CfgAion _cfgAion,
            AionBlockchainImpl _blockchain,
            PendingTxCallback pendingTxCallback,
            NetworkBestBlockCallback networkBestBlockCallback,
            TransactionBroadcastCallback transactionBroadcastCallback,
            boolean forTest) {
        initializeHub(_cfgAion, _blockchain, pendingTxCallback, networkBestBlockCallback,
            transactionBroadcastCallback, forTest);
    }

    private void registerCallback() {
        List<Handler> cbs = new ArrayList<>();
        cbs.add(
                new ReqStatusHandler(
                        syncLOG,
                        blockchain,
                        mempool,
                        p2pMgr,
                        cfg.getGenesis().getHash(),
                        apiVersion));
        cbs.add(new ResStatusHandler(syncLOG, surveyLOG, p2pMgr, syncMgr));
        boolean inSyncOnlyMode = cfg.getNet().getP2p().inSyncOnlyMode();
        cbs.add(new ReqBlocksHeadersHandler(syncLOG, blockchain, p2pMgr, inSyncOnlyMode));
        cbs.add(new ResBlocksHeadersHandler(syncLOG, surveyLOG, syncMgr, p2pMgr));
        cbs.add(new ReqBlocksBodiesHandler(syncLOG, blockchain, syncMgr, p2pMgr, inSyncOnlyMode));
        cbs.add(new ResBlocksBodiesHandler(syncLOG, surveyLOG, syncMgr, p2pMgr));
        cbs.add(new BroadcastTxHandler(syncLOG, mempool, p2pMgr, inSyncOnlyMode));
        cbs.add(new BroadcastNewBlockHandler(syncLOG, surveyLOG, propHandler, p2pMgr));
        this.p2pMgr.register(cbs);
    }

    private void loadEventMgr(boolean forTest) {

        try {
            ServiceLoader.load(EventMgrModule.class);
        } catch (Exception e) {
            genLOG.error("load EventMgr service fail!" + e.toString());
            throw e;
        }

        Properties prop = new Properties();
        // TODO : move module name to config file
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        try {
            this.eventMgr = EventMgrModule.getSingleton(prop).getEventMgr();
        } catch (Exception e) {
            genLOG.error("Can not load the Event Manager Module", e);
        }

        if (eventMgr == null) {
            throw new NullPointerException();
        }

        if (!forTest) {
            this.eventMgr.start();
        }
    }

    public Repository getRepository() {
        return blockchain.getRepository();
    }

    public IAionBlockchain getBlockchain() {
        return blockchain;
    }

    public BigInteger getTotalDifficultyForHash(byte[] hash) {
        return this.blockchain.getTotalDifficultyForHash(hash);
    }

    public AionPendingStateImpl getPendingState() {
        return mempool;
    }

    public IEventMgr getEventMgr() {
        return this.eventMgr;
    }

    public BlockPropagationHandler getPropHandler() {
        return propHandler;
    }

    public void close() {
        genLOG.info("<KERNEL SHUTDOWN SEQUENCE>");

        if (syncMgr != null) {
            syncMgr.shutdown();
            genLOG.info("<shutdown-sync-mgr>");
        }

        if (p2pMgr != null) {
            p2pMgr.shutdown();
            genLOG.info("<shutdown-p2p-mgr>");
        }

        if (eventMgr != null) {
            try {
                eventMgr.shutDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (pow != null) {
            genLOG.info("shutting down consensus...");
            pow.shutdown();
            genLOG.info("shutdown consensus... Done!");
        }

        blockchain.close();

        this.start.set(false);
    }

    public SyncMgr getSyncMgr() {
        return this.syncMgr;
    }

    /** Note: method used only by AionImpl and tests. Please avoid further use. */
    public IP2pMgr getP2pMgr() {
        return this.p2pMgr;
    }

    public int getActiveNodesCount() {
        return this.p2pMgr.getActiveNodes().size();
    }

    public List<Short> getP2pVersions() {
        return this.p2pMgr.versions();
    }

    public int getChainId() {
        return this.chainId;
    }

    public Map<Integer, NodeWrapper> getActiveNodes() {
        Map<Integer, NodeWrapper> active = new HashMap<>();
        for (Map.Entry<Integer, INode> entry : this.p2pMgr.getActiveNodes().entrySet()) {
            active.put(entry.getKey(), new NodeWrapper(entry.getValue()));
        }
        return active;
    }

    public static String getRepoVersion() {
        return Version.REPO_VERSION;
    }

    public Block getStartingBlock() {
        return this.startingBlock;
    }

    public static byte getApiVersion() {
        return apiVersion;
    }

    private void registerBlockEvents() {
        if (this.eventMgr != null) {
            List<IEvent> evts = new ArrayList<>();
            evts.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
            evts.add(new EventBlock(EventBlock.CALLBACK.ONTRACE0));

            this.eventMgr.registerEvent(evts);
        } else {
            genLOG.error("Event manager is null!");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }
    }

    // Returns a new template if a better parent block to mine on is found, or if the system time
    // is ahead of the oldBlockTemplate
    // Returns null if we're waiting on a Staking block, or if creating a new block template failed for some reason
    public BlockContext getNewMiningBlockTemplate(BlockContext oldBlockTemplate, long systemTime) {
        if (blockchain.isUnityForkEnabledAtNextBlock() &&
                blockchain.getBestBlock().getHeader().getSealType() == BlockHeader.BlockSealType.SEAL_POW_BLOCK) {
            return null;
        } else {
            BlockContext context;
            
            blockTemplateLock.lock();
            try {
                Block bestBlock = blockchain.getBestBlock();
                byte[] bestBlockHash = bestBlock.getHash();

                if (oldBlockTemplate == null
                        || !Arrays.equals(bestBlockHash, oldBlockTemplate.block.getParentHash())
                        || (systemTime > oldBlockTemplate.block.getTimestamp() && blockchain.isUnityForkEnabledAtNextBlock())) {

                    TransactionSortedSet txSortSet = new TransactionSortedSet();
                    txSortSet.addAll(mempool.getPendingTransactions());

                    context =
                            blockchain.createNewMiningBlockContext(
                                    bestBlock, new ArrayList<>(txSortSet), false);
                } else {
                    context = oldBlockTemplate;
                }
            } finally {
                blockTemplateLock.unlock();
            }

            return context;
        }
    }
    
    // Returns null if we're waiting on a Mining block, or if creating a new block template failed for some reason
    public StakingBlock getStakingBlockTemplate(byte[] newSeed, byte[] signingPublicKey, byte[] coinbase) {
        if (blockchain.getBestBlock().getHeader().getSealType() == BlockHeader.BlockSealType.SEAL_POS_BLOCK) {
            return null;
        } else {
            StakingBlock blockTemplate;
            blockTemplateLock.lock();
            try {
                blockTemplate = blockchain.createStakingBlockTemplate(
                        mempool.getPendingTransactions(), signingPublicKey, newSeed, coinbase);
            } finally {
                blockTemplateLock.unlock();
            }

            return blockTemplate;
        }
    }

    public void enableUnityFork(long unityForkNumber) {
        this.blockchain.forkUtility.enableUnityFork(unityForkNumber);
    }

    @VisibleForTesting
    public void disableUnityFork() {
        this.blockchain.forkUtility.disableUnityFork();
    }

    class SelfNodeStatusCallback {
        final IP2pMgr p2pMgr;

        SelfNodeStatusCallback(IP2pMgr p2pMgr) {
            if (p2pMgr == null) {
                throw new IllegalStateException("Null p2pMgr instance!");
            }
            this.p2pMgr = p2pMgr;
        }

        void updateBlockStatus(long number, byte[] hash, BigInteger td) {
            p2pMgr.updateChainInfo(number, hash, td);
        }
    }

    class BestBlockImportCallback {
        final IPendingState mempool;

        BestBlockImportCallback(IPendingState mempool) {
            if (mempool == null) {
                throw new IllegalStateException("Mempool input is null!");
            }

            this.mempool = mempool;
        }

        void applyBlockUpdate(Block block, List<AionTxReceipt> receipts) {
            mempool.applyBlockUpdate(block, receipts);
        }

    }

    private class TransactionSortedSet extends TreeSet<AionTransaction> {

        TransactionSortedSet() {
            super(
                (tx1, tx2) -> {
                    long nonceDiff =
                        ByteUtil.byteArrayToLong(tx1.getNonce())
                            - ByteUtil.byteArrayToLong(tx2.getNonce());

                    if (nonceDiff != 0) {
                        return nonceDiff > 0 ? 1 : -1;
                    }
                    return Arrays.compare(tx1.getTransactionHash(), tx2.getTransactionHash());
                });
        }
    }
}
