/*
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
 */
package org.aion.zero.impl;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.base.db.IRepository;
import org.aion.base.util.ByteUtil;
import org.aion.evtmgr.EventMgrModule;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogUtil;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.config.CfgNetP2p;
import org.aion.mcf.db.IBlockStorePow;
import org.aion.mcf.tx.ITransactionExecThread;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.RecoveryUtils;
import org.aion.zero.impl.pow.AionPoW;
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
import org.aion.zero.impl.tx.AionTransactionExecThread;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

public class AionHub {

    private static final Logger genLOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    private static final Logger syncLOG = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    private IP2pMgr p2pMgr;

    private CfgAion cfg;

    private SyncMgr syncMgr;

    private BlockPropagationHandler propHandler;

    private IPendingStateInternal<AionBlock, AionTransaction> mempool;

    private IAionBlockchain blockchain;

    // TODO: Refactor to interface later
    private AionRepositoryImpl repository;

    private ITransactionExecThread<AionTransaction> txThread;

    private IEventMgr eventMgr;

    private AionPoW pow;

    private AtomicBoolean start = new AtomicBoolean(true);

    /** Test functionality for checking if the hub has been shut down. */
    public boolean isRunning() {
        return start.get();
    }

    /**
     * A "cached" block that represents our local best block when the application is first booted.
     */
    private volatile AionBlock startingBlock;

    /**
     * Initialize as per the <a href=
     * "https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">Initialization-on-demand</a>
     * holder pattern
     */
    private static class Holder {
        static final AionHub INSTANCE = new AionHub();
    }

    public static AionHub inst() {
        return Holder.INSTANCE;
    }

    private static final int INIT_ERROR_EXIT_CODE = -1;

    public AionHub() {
        initializeHub(CfgAion.inst(), AionBlockchainImpl.inst(), AionRepositoryImpl.inst(), false);
    }

    private void initializeHub(
            CfgAion _cfgAion,
            AionBlockchainImpl _blockchain,
            AionRepositoryImpl _repository,
            boolean forTest) {

        this.cfg = _cfgAion;

        // load event manager before init blockchain instance
        loadEventMgr(forTest);

        AionBlockchainImpl blockchain = _blockchain;
        blockchain.setEventManager(this.eventMgr);
        this.blockchain = blockchain;

        this.repository = _repository;

        this.mempool =
                forTest
                        ? AionPendingStateImpl.createForTesting(_cfgAion, _blockchain, _repository)
                        : AionPendingStateImpl.inst();

        this.txThread =
                forTest
                        ? AionTransactionExecThread.createForTesting((AionPendingStateImpl) mempool)
                        : AionTransactionExecThread.getInstance();

        loadBlockchain();

        this.startingBlock = this.blockchain.getBestBlock();
        if (!cfg.getConsensus().isSeed()) {
            this.mempool.updateBest();

            if (cfg.getTx().getPoolBackup()) {
                this.mempool.loadPendingTx();
            }
        } else {
            genLOG.info("Seed node mode enabled!");
        }

        /*
         * p2p hook up start sync mgr needs to be initialed after loadBlockchain()
         * method
         */
        CfgNetP2p cfgNetP2p = this.cfg.getNet().getP2p();

        // there are two p2p implementation , now just point to impl1.
        this.p2pMgr =
                new P2pMgr(
                        this.cfg.getNet().getId(),
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

        this.syncMgr = SyncMgr.inst();
        this.syncMgr.init(
                blockchain,
                p2pMgr,
                eventMgr,
                cfg.getSync().getBlocksQueueMax(),
                cfg.getSync().getShowStatus());

        ChainConfiguration chainConfig = new ChainConfiguration();
        this.propHandler =
                new BlockPropagationHandler(
                        1024,
                        this.blockchain,
                        p2pMgr,
                        chainConfig.createBlockHeaderValidator(),
                        cfg.getNet().getP2p().inSyncOnlyMode());

        registerCallback();
        p2pMgr.run();

        ((AionPendingStateImpl) this.mempool).setP2pMgr(this.p2pMgr);

        this.pow = new AionPoW();
        this.pow.init(blockchain, mempool, eventMgr);
    }

    static AionHub createForTesting(
            CfgAion _cfgAion, AionBlockchainImpl _blockchain, AionRepositoryImpl _repository) {
        return new AionHub(_cfgAion, _blockchain, _repository, true);
    }

    private AionHub(
            CfgAion _cfgAion,
            AionBlockchainImpl _blockchain,
            AionRepositoryImpl _repository,
            boolean forTest) {
        initializeHub(_cfgAion, _blockchain, _repository, forTest);
    }

    private void registerCallback() {
        List<Handler> cbs = new ArrayList<>();
        cbs.add(new ReqStatusHandler(syncLOG, blockchain, p2pMgr, cfg.getGenesis().getHash()));
        cbs.add(new ResStatusHandler(syncLOG, p2pMgr, syncMgr));
        boolean inSyncOnlyMode = cfg.getNet().getP2p().inSyncOnlyMode();
        cbs.add(new ReqBlocksHeadersHandler(syncLOG, blockchain, p2pMgr, inSyncOnlyMode));
        cbs.add(new ResBlocksHeadersHandler(syncLOG, syncMgr, p2pMgr));
        cbs.add(new ReqBlocksBodiesHandler(syncLOG, blockchain, p2pMgr, inSyncOnlyMode));
        cbs.add(new ResBlocksBodiesHandler(syncLOG, syncMgr, p2pMgr));
        cbs.add(new BroadcastTxHandler(syncLOG, mempool, p2pMgr, inSyncOnlyMode));
        cbs.add(new BroadcastNewBlockHandler(syncLOG, propHandler, p2pMgr));
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
        } catch (Throwable e) {
            genLOG.error("Can not load the Event Manager Module", e.getMessage());
        }

        if (eventMgr == null) {
            throw new NullPointerException();
        }

        if (!forTest) {
            this.eventMgr.start();
        }
    }

    public IRepository getRepository() {
        return repository;
    }

    public IAionBlockchain getBlockchain() {
        return blockchain;
    }

    public IBlockStorePow<AionBlock, A0BlockHeader> getBlockStore() {
        return this.repository.getBlockStore();
    }

    public IPendingStateInternal<AionBlock, AionTransaction> getPendingState() {
        return mempool;
    }

    public IEventMgr getEventMgr() {
        return this.eventMgr;
    }

    public BlockPropagationHandler getPropHandler() {
        return propHandler;
    }

    private void loadBlockchain() {

        // function repurposed for integrity checks since previously not implemented
        try {
            this.repository.getBlockStore().load();
        } catch (RuntimeException re) {
            genLOG.error("Fatal: can't load blockstore; exiting.", re);
            System.exit(INIT_ERROR_EXIT_CODE);
        }

        AionBlock bestBlock = this.repository.getBlockStore().getBestBlock();
        if (bestBlock != null) {
            bestBlock.setCumulativeDifficulty(
                    repository.getBlockStore().getTotalDifficultyForHash(bestBlock.getHash()));
        }

        boolean recovered = true;
        boolean bestBlockShifted = true;
        int countRecoveryAttempts = 0;

        // fix the trie if necessary
        while (bestBlockShifted
                && // the best block was updated after recovery attempt
                (countRecoveryAttempts < 5)
                && // allow 5 recovery attempts
                bestBlock != null
                && // recover only for non-null blocks
                !this.repository.isValidRoot(bestBlock.getStateRoot())) {

            genLOG.info(
                    "Recovery initiated due to corrupt world state at block "
                            + bestBlock.getNumber()
                            + ".");

            long bestBlockNumber = bestBlock.getNumber();
            byte[] bestBlockRoot = bestBlock.getStateRoot();

            // ensure that the genesis state exists before attempting recovery
            AionGenesis genesis = cfg.getGenesis();
            if (!this.repository.isValidRoot(genesis.getStateRoot())) {
                genLOG.info(
                        "Corrupt world state for genesis block hash: "
                                + genesis.getShortHash()
                                + ", number: "
                                + genesis.getNumber()
                                + ".");

                AionHubUtils.buildGenesis(genesis, repository);

                if (repository.isValidRoot(genesis.getStateRoot())) {
                    genLOG.info("Rebuilding genesis block SUCCEEDED.");
                } else {
                    genLOG.info("Rebuilding genesis block FAILED.");
                }
            }

            recovered = this.blockchain.recoverWorldState(this.repository, bestBlock);

            if (!this.repository.isValidRoot(bestBlock.getStateRoot())) {
                // reverting back one block
                genLOG.info("Rebuild state FAILED. Reverting to previous block.");

                long blockNumber = bestBlock.getNumber() - 1;
                RecoveryUtils.Status status = RecoveryUtils.revertTo(this.blockchain, blockNumber);

                recovered =
                        (status == RecoveryUtils.Status.SUCCESS)
                                && this.repository.isValidRoot(
                                        this.repository
                                                .getBlockStore()
                                                .getChainBlockByNumber(blockNumber)
                                                .getStateRoot());
            }

            if (recovered) {
                bestBlock = this.repository.getBlockStore().getBestBlock();
                if (bestBlock != null) {
                    bestBlock.setCumulativeDifficulty(
                            repository
                                    .getBlockStore()
                                    .getTotalDifficultyForHash(bestBlock.getHash()));
                }

                // checking is the best block has changed since attempting recovery
                if (bestBlock == null) {
                    bestBlockShifted = true;
                } else {
                    bestBlockShifted =
                            !(bestBlockNumber == bestBlock.getNumber())
                                    || // block number changed
                                    !(Arrays.equals(
                                            bestBlockRoot,
                                            bestBlock.getStateRoot())); // root hash changed
                }

                if (bestBlockShifted) {
                    genLOG.info(
                            "Rebuilding world state SUCCEEDED by REVERTING to a previous block.");
                } else {
                    genLOG.info("Rebuilding world state SUCCEEDED.");
                }
            } else {
                genLOG.error(
                        "Rebuilding world state FAILED. "
                                + "Stop the kernel (Ctrl+C) and use the command line revert option to move back to a valid block. "
                                + "Check the Aion wiki for recommendations on choosing the block number.");
            }

            countRecoveryAttempts++;
        }

        // rebuild from genesis if (1) no best block (2) recovery failed
        if (bestBlock == null || !recovered) {
            if (bestBlock == null) {
                genLOG.info("DB is empty - adding Genesis");
            } else {
                genLOG.info("DB could not be recovered - adding Genesis");
            }

            AionGenesis genesis = cfg.getGenesis();

            AionHubUtils.buildGenesis(genesis, repository);

            blockchain.setBestBlock(genesis);
            blockchain.setTotalDifficulty(genesis.getDifficultyBI());
            if (genesis.getCumulativeDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                genesis.setCumulativeDifficulty(genesis.getDifficultyBI());
            }

            if (this.eventMgr != null) {
                List<IEvent> evts = new ArrayList<>();
                evts.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
                evts.add(new EventBlock(EventBlock.CALLBACK.ONTRACE0));

                this.eventMgr.registerEvent(evts);
            } else {
                genLOG.error("Event manager is null !!!");
                System.exit(INIT_ERROR_EXIT_CODE);
            }

            genLOG.info(
                    "loaded genesis block <num={}, root={}>",
                    0,
                    ByteUtil.toHexString(genesis.getStateRoot()));

        } else {

            blockchain.setBestBlock(bestBlock);
            blockchain.setTotalDifficulty(this.repository.getBlockStore().getTotalDifficulty());
            if (bestBlock.getCumulativeDifficulty().equals(BigInteger.ZERO)) {
                // setting the object runtime value
                bestBlock.setCumulativeDifficulty(
                        this.repository.getBlockStore().getTotalDifficulty());
            }

            genLOG.info(
                    "loaded block <num={}, root={}>",
                    blockchain.getBestBlock().getNumber(),
                    LogUtil.toHexF8(blockchain.getBestBlock().getStateRoot()));
        }

        byte[] genesisHash = cfg.getGenesis().getHash();
        byte[] databaseGenHash =
                blockchain.getBlockByNumber(0) == null
                        ? null
                        : blockchain.getBlockByNumber(0).getHash();

        // this indicates that DB and genesis are inconsistent
        if (genesisHash == null
                || databaseGenHash == null
                || (!Arrays.equals(genesisHash, databaseGenHash))) {
            if (genesisHash == null) {
                genLOG.error("failed to load genesis from config");
            }

            if (databaseGenHash == null) {
                genLOG.error("failed to load block 0 from database");
            }

            genLOG.error(
                    "genesis json rootHash {} is inconsistent with database rootHash {}\n"
                            + "your configuration and genesis are incompatible, please do the following:\n"
                            + "\t1) Remove your database folder\n"
                            + "\t2) Verify that your genesis is correct by re-downloading the binary or checking online\n"
                            + "\t3) Reboot with correct genesis and empty database\n",
                    genesisHash == null ? "null" : ByteUtil.toHexString(genesisHash),
                    databaseGenHash == null ? "null" : ByteUtil.toHexString(databaseGenHash));
            System.exit(INIT_ERROR_EXIT_CODE);
        }

        if (!Arrays.equals(blockchain.getBestBlock().getStateRoot(), EMPTY_TRIE_HASH)) {
            this.repository.syncToRoot(blockchain.getBestBlock().getStateRoot());
        }

        //        this.repository.getBlockStore().load();
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

        if (txThread != null) {
            txThread.shutdown();
            genLOG.info("<shutdown-tx>");
        }

        if (eventMgr != null) {
            try {
                eventMgr.shutDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (getPendingState() != null) {
            getPendingState().shutDown();
            genLOG.info("<shutdown-pendingState>");
        }

        genLOG.info("shutting down consensus...");
        pow.shutdown();
        genLOG.info("shutdown consensus... Done!");

        if (repository != null) {
            genLOG.info("shutting down DB...");
            repository.close();
            genLOG.info("shutdown DB... Done!");
        }

        this.start.set(false);
    }

    public SyncMgr getSyncMgr() {
        return this.syncMgr;
    }

    public IP2pMgr getP2pMgr() {
        return this.p2pMgr;
    }

    public static String getRepoVersion() {
        return Version.REPO_VERSION;
    }

    public AionBlock getStartingBlock() {
        return this.startingBlock;
    }
}
