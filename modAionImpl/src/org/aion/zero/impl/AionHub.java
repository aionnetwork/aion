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

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
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
import org.aion.mcf.vm.types.DataWord;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.utils.TaskDumpHeap;
import org.aion.vm.PrecompiledContracts;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.RecoveryUtils;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.handler.*;
import org.aion.zero.impl.tx.AionTransactionExecThread;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;

public class AionHub {

	private static final Logger LOG = LoggerFactory.getLogger(LogEnum.GEN.name());

	private static final Logger syncLog = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

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

	/**
	 * A "cached" block that represents our local best block when the application is
	 * first booted.
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

	public AionHub() {

		this.cfg = CfgAion.inst();

		// load event manager before init blockchain instance
		loadEventMgr();

		AionBlockchainImpl blockchain = AionBlockchainImpl.inst();
		blockchain.setEventManager(this.eventMgr);
		this.blockchain = blockchain;

		this.repository = AionRepositoryImpl.inst();

		this.mempool = AionPendingStateImpl.inst();

		this.txThread = AionTransactionExecThread.getInstance();

		loadBlockchain();

        this.startingBlock = this.blockchain.getBestBlock();
        if (!cfg.getConsensus().isSeed()) {
            this.mempool.updateBest();

            if (cfg.getTx().getPoolBackup()) {
                this.mempool.loadPendingTx();
            }
        } else {
            LOG.info("Seed node mode enabled!");
        }

		String reportsFolder = "";
		if (cfg.getReports().isEnabled()) {
			File rpf = new File(cfg.getBasePath(), cfg.getReports().getPath());
			rpf.mkdirs();
			reportsFolder = rpf.getAbsolutePath();
		}

		/*
		 * p2p hook up start sync mgr needs to be initialed after loadBlockchain()
		 * method
		 */
		CfgNetP2p cfgNetP2p = this.cfg.getNet().getP2p();

		// there two p2p impletation , now just point to impl1.
		this.p2pMgr = new P2pMgr(this.cfg.getNet().getId(), Version.KERNEL_VERSION, this.cfg.getId(), cfgNetP2p.getIp(),
				cfgNetP2p.getPort(), this.cfg.getNet().getNodes(), cfgNetP2p.getDiscover(), cfgNetP2p.getMaxTempNodes(),
				cfgNetP2p.getMaxActiveNodes(), cfgNetP2p.getShowStatus(), cfgNetP2p.getShowLog(),
				cfgNetP2p.getBootlistSyncOnly(), cfgNetP2p.getErrorTolerance());

		this.syncMgr = SyncMgr.inst();
		this.syncMgr.init(this.p2pMgr, this.eventMgr, this.cfg.getSync().getBlocksQueueMax(),
				this.cfg.getSync().getShowStatus(), this.cfg.getReports().isEnabled(), reportsFolder);

		ChainConfiguration chainConfig = new ChainConfiguration();
		this.propHandler = new BlockPropagationHandler(1024, this.blockchain, this.p2pMgr,
				chainConfig.createBlockHeaderValidator(), this.cfg.getNet().getP2p().isSyncOnlyNode());

		registerCallback();
		this.p2pMgr.run();

        ((AionPendingStateImpl)this.mempool).setP2pMgr(this.p2pMgr);

		this.pow = new AionPoW();
		this.pow.init(blockchain, mempool, eventMgr);

        if (cfg.getReports().isHeapDumpEnabled()) {
            new Thread(new TaskDumpHeap(this.start, cfg.getReports().getHeapDumpInterval(), reportsFolder), "dump-heap")
                    .start();
        }
    }

    private void registerCallback() {
        List<Handler> cbs = new ArrayList<>();
        cbs.add(new ReqStatusHandler(syncLog, this.blockchain, this.p2pMgr, cfg.getGenesis().getHash()));
        cbs.add(new ResStatusHandler(syncLog, this.p2pMgr, this.syncMgr));
        cbs.add(new ReqBlocksHeadersHandler(syncLog, this.blockchain, this.p2pMgr, this.cfg.getNet().getP2p().isSyncOnlyNode()));
        cbs.add(new ResBlocksHeadersHandler(syncLog, this.syncMgr, this.p2pMgr));
        cbs.add(new ReqBlocksBodiesHandler(syncLog, this.blockchain, this.p2pMgr, this.cfg.getNet().getP2p().isSyncOnlyNode()));
        cbs.add(new ResBlocksBodiesHandler(syncLog, this.syncMgr, this.p2pMgr));
        cbs.add(new BroadcastTxHandler(syncLog, this.mempool, this.p2pMgr, this.cfg.getNet().getP2p().isSyncOnlyNode()));
        cbs.add(new BroadcastNewBlockHandler(syncLog, this.propHandler, this.p2pMgr));
        this.p2pMgr.register(cbs);
    }

    /**
     */
    private void loadEventMgr() {

        try {
            ServiceLoader.load(EventMgrModule.class);
        } catch (Exception e) {
            LOG.error("load EventMgr service fail!" + e.toString());
            throw e;
        }

        Properties prop = new Properties();
        // TODO : move module name to config file
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        try {
            this.eventMgr = EventMgrModule.getSingleton(prop).getEventMgr();
        } catch (Throwable e) {
            LOG.error("Can not load the Event Manager Module", e.getMessage());
        }

        if (eventMgr == null) {
            throw new NullPointerException();
        }

        this.eventMgr.start();
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

    public ITransactionExecThread<AionTransaction> getTxThread() {
        return this.txThread;
    }

    public BlockPropagationHandler getPropHandler() {
        return propHandler;
    }

    private void loadBlockchain() {

        // function repurposed for integrity checks since previously not implemented
        this.repository.getBlockStore().load();

        AionBlock bestBlock = this.repository.getBlockStore().getBestBlock();
        if (bestBlock != null) {
            bestBlock
                    .setCumulativeDifficulty(repository.getBlockStore().getTotalDifficultyForHash(bestBlock.getHash()));
        }

        boolean recovered = true;
        boolean bestBlockShifted = true;
        int countRecoveryAttempts = 0;

        // fix the trie if necessary
        while (bestBlockShifted && // the best block was updated after recovery attempt
                (countRecoveryAttempts < 5) && // allow 5 recovery attempts
                bestBlock != null && // recover only for non-null blocks
                !this.repository.isValidRoot(bestBlock.getStateRoot())) {

            LOG.info("Recovery initiated due to corrupt world state at block " + bestBlock.getNumber() + ".");

            long bestBlockNumber = bestBlock.getNumber();
            byte[] bestBlockRoot = bestBlock.getStateRoot();

            // ensure that the genesis state exists before attempting recovery
            AionGenesis genesis = cfg.getGenesis();
            if (!this.repository.isValidRoot(genesis.getStateRoot())) {
                LOG.info(
                        "Corrupt world state for genesis block hash: " + genesis.getShortHash() + ", number: " + genesis
                                .getNumber() + ".");

                AionHubUtils.buildGenesis(genesis, repository);

                if (repository.isValidRoot(genesis.getStateRoot())) {
                    LOG.info("Rebuilding genesis block SUCCEEDED.");
                } else {
                    LOG.info("Rebuilding genesis block FAILED.");
                }
            }

            recovered = this.blockchain.recoverWorldState(this.repository, bestBlock);

            if (!this.repository.isValidRoot(bestBlock.getStateRoot())) {
                // reverting back one block
                LOG.info("Rebuild state FAILED. Reverting to previous block.");

                long blockNumber = bestBlock.getNumber() - 1;
                RecoveryUtils.Status status = RecoveryUtils.revertTo(this.blockchain, blockNumber);

                recovered = (status == RecoveryUtils.Status.SUCCESS) && this.repository
                        .isValidRoot(this.repository.getBlockStore().getChainBlockByNumber(blockNumber).getStateRoot());
            }

            if (recovered) {
                bestBlock = this.repository.getBlockStore().getBestBlock();
                if (bestBlock != null) {
                    bestBlock.setCumulativeDifficulty(repository.getBlockStore()
                                                              .getTotalDifficultyForHash(bestBlock.getHash()));
                }

                // checking is the best block has changed since attempting recovery
                if (bestBlock == null) {
                    bestBlockShifted = true;
                } else {
                    bestBlockShifted = !(bestBlockNumber == bestBlock.getNumber()) || // block number changed
                            !(Arrays.equals(bestBlockRoot, bestBlock.getStateRoot())); // root hash changed
                }

                if (bestBlockShifted) {
                    LOG.info("Rebuilding world state SUCCEEDED by REVERTING to a previous block.");
                } else {
                    LOG.info("Rebuilding world state SUCCEEDED.");
                }
            } else {
                LOG.error("Rebuilding world state FAILED. "
                        + "Stop the kernel (Ctrl+C) and use the command line revert option to move back to a valid block. "
                        + "Check the Aion wiki for recommendations on choosing the block number.");
            }

            countRecoveryAttempts++;
        }

        // rebuild from genesis if (1) no best block (2) recovery failed
        if (bestBlock == null || !recovered) {
            if (bestBlock == null) {
                LOG.info("DB is empty - adding Genesis");
            } else {
                LOG.info("DB could not be recovered - adding Genesis");
            }

            AionGenesis genesis = cfg.getGenesis();

            AionHubUtils.buildGenesis(genesis, repository);

            blockchain.setBestBlock(genesis);
            blockchain.setTotalDifficulty(genesis.getDifficultyBI());

            if (this.eventMgr != null) {
                List<IEvent> evts = new ArrayList<>();
                evts.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
                evts.add(new EventBlock(EventBlock.CALLBACK.ONTRACE0));

                this.eventMgr.registerEvent(evts);
            } else {
                LOG.error("Event manager is null !!!");
                System.exit(-1);
            }

            LOG.info("loaded genesis block <num={}, root={}>", 0, ByteUtil.toHexString(genesis.getStateRoot()));

        } else {

            blockchain.setBestBlock(bestBlock);
            blockchain.setTotalDifficulty(this.repository.getBlockStore().getTotalDifficulty());
            LOG.info("loaded block <num={}, root={}>", blockchain.getBestBlock().getNumber(),
                    LogUtil.toHexF8(blockchain.getBestBlock().getStateRoot()));
        }

        byte[] genesisHash = cfg.getGenesis().getHash();
        byte[] databaseGenHash = blockchain.getBlockByNumber(0) == null ? null : blockchain.getBlockByNumber(0).getHash();

        // this indicates that DB and genesis are inconsistent
        if (genesisHash == null || databaseGenHash == null || (!Arrays.equals(genesisHash, databaseGenHash))) {
            if (genesisHash == null) {
                LOG.error("failed to load genesis from config");
            }

            if (databaseGenHash == null) {
                LOG.error("failed to load block 0 from database");
            }

            LOG.error("genesis json rootHash {} is inconsistent with database rootHash {}\n" +
                            "your configuration and genesis are incompatible, please do the following:\n" +
                            "\t1) Remove your database folder\n" +
                            "\t2) Verify that your genesis is correct by re-downloading the binary or checking online\n" +
                            "\t3) Reboot with correct genesis and empty database\n",
                    genesisHash == null ? "null" : ByteUtil.toHexString(genesisHash),
                    databaseGenHash == null ? "null" : ByteUtil.toHexString(databaseGenHash));
            System.exit(-1);
        }

        if (!Arrays.equals(blockchain.getBestBlock().getStateRoot(), EMPTY_TRIE_HASH)) {
            this.repository.syncToRoot(blockchain.getBestBlock().getStateRoot());
        }

//        this.repository.getBlockStore().load();
    }

    public void close() {
        LOG.info("<KERNEL SHUTDOWN SEQUENCE>");

        if (syncMgr != null) {
            syncMgr.shutdown();
            LOG.info("<shutdown-sync-mgr>");
        }

        if (p2pMgr != null) {
            p2pMgr.shutdown();
            LOG.info("<shutdown-p2p-mgr>");
        }

        if (txThread != null) {
            txThread.shutdown();
            LOG.info("<shutdown-tx>");
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
            LOG.info("<shutdown-pendingState>");
        }

        LOG.info("shutting down consensus...");
        pow.shutdown();
        LOG.info("shutdown consensus... Done!");

        if (repository != null) {
            LOG.info("shutting down DB...");
            repository.close();
            LOG.info("shutdown DB... Done!");
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
