package org.aion.zero.impl.blockchain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.aion.zero.impl.types.PendingTxDetails;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.equihash.EquihashMiner;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.base.AccountState;
import org.aion.zero.impl.core.ImportResult;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.tx.TxCollector;
import org.aion.base.AionTxReceipt;
import org.slf4j.Logger;

public class AionImpl implements IAionChain {

    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());
    private static final Logger LOG_TX = AionLoggerFactory.getLogger(LogEnum.TX.toString());
    private static final Logger LOG_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    static final ECKey keyForCallandEstimate = ECKeyFac.inst().fromPrivate(new byte[64]);

    public AionHub aionHub;

    private CfgAion cfg;

    private TxCollector collector;

    private EquihashMiner equihashMiner;

    private List<BlockchainCallbackInterface> blockchainCallbackInterfaces = Collections.synchronizedList(new ArrayList<>());

    private AionImpl(boolean forTest) {
        this.cfg = CfgAion.inst();
        if (forTest) {
            cfg.setGenesisForTest();
            aionHub =
                    AionHub.createForTesting(
                            cfg,
                            new AionBlockchainImpl(cfg, null, true),
                            new PendingTxCallback(blockchainCallbackInterfaces),
                            new NetworkBestBlockCallback(this),
                            new TransactionBroadcastCallback(this));
        } else {
            aionHub = new AionHub(new PendingTxCallback(blockchainCallbackInterfaces), new NetworkBestBlockCallback(this), new TransactionBroadcastCallback(this));
        }

        LOG_GEN.info(
                "<node-started endpoint=p2p://"
                        + cfg.getId()
                        + "@"
                        + cfg.getNet().getP2p().getIp()
                        + ":"
                        + cfg.getNet().getP2p().getPort()
                        + ">");

        collector = new TxCollector(this.aionHub.getP2pMgr(), LOG_TX);
    }

    public static AionImpl inst() {
        return Holder.INSTANCE;
    }

    public static AionImpl instForTest() {
        return HolderForTest.INSTANCE;
    }

    @Override
    public UnityChain getBlockchain() {
        return aionHub.getBlockchain();
    }

    public synchronized ImportResult addNewBlock(Block block) {
        ImportResult importResult =
                this.aionHub
                        .getBlockchain()
                        .tryToConnect(new BlockWrapper(block, true, false, false, false));

        if (importResult == ImportResult.IMPORTED_BEST) {
            this.aionHub.getPropHandler().propagateNewBlock(block);
        }

        return importResult;
    }

    @Override
    public EquihashMiner getBlockMiner() {

        if (equihashMiner == null) {
            try {
                equihashMiner = new EquihashMiner();
            } catch (Exception e) {
                LOG_GEN.error("Init miner failed!", e);
                return null;
            }
        }
        return equihashMiner;
    }

    @Override
    public void close() {
        aionHub.close();
    }

    @Override
    public void broadcastTransactions(List<AionTransaction> transactions) {
        collector.submitTx(transactions);
    }

    public long estimateTxNrg(AionTransaction tx, Block block) {
        RepositoryCache repository =
                aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = true;
            boolean incrementSenderNonce = true;
            boolean fork040enabled = aionHub.isFork040Active(block.getNumber());
            boolean checkBlockEnergyLimit = false;
            boolean unityForkEnabled = aionHub.isForkUnityActive(block.getNumber());
            boolean signatureSwapForkEnabled = aionHub.isForkSignatureSwapActive(block.getNumber());

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                            block.getDifficulty(),
                            block.getNumber(),
                            block.getTimestamp(),
                            block.getNrgLimit(),
                            block.getCoinbase(),
                            tx,
                            repository,
                            isLocalCall,
                            incrementSenderNonce,
                            fork040enabled,
                            checkBlockEnergyLimit,
                            LOG_VM,
                            BlockCachingContext.CALL,
                            block.getNumber(),
                            unityForkEnabled,
                            signatureSwapForkEnabled)
                    .getReceipt()
                    .getEnergyUsed();
        } catch (VmFatalException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(SystemExitCodes.FATAL_VM_ERROR);
            return 0;
        } finally {
            repository.rollback();
        }
    }

    @Override
    public AionTxReceipt callConstant(AionTransaction tx, Block block) {
        RepositoryCache repository =
                aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = true;
            boolean incrementSenderNonce = true;
            boolean fork040enabled = aionHub.isFork040Active(block.getNumber());
            boolean checkBlockEnergyLimit = false;
            boolean unityForkEnabled = aionHub.isForkUnityActive(block.getNumber());
            boolean signatureSwapForkEnabled = aionHub.isForkSignatureSwapActive(block.getNumber());

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                            block.getDifficulty(),
                            block.getNumber(),
                            block.getTimestamp(),
                            block.getNrgLimit(),
                            block.getCoinbase(),
                            tx,
                            repository,
                            isLocalCall,
                            incrementSenderNonce,
                            fork040enabled,
                            checkBlockEnergyLimit,
                            LOG_VM,
                            BlockCachingContext.CALL,
                            block.getNumber(),
                            unityForkEnabled,
                            signatureSwapForkEnabled)
                    .getReceipt();
        } catch (VmFatalException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(SystemExitCodes.FATAL_VM_ERROR);
            return null;
        } finally {
            repository.rollback();
        }
    }

    @Override
    public Repository getRepository() {
        return aionHub.getRepository();
    }

    @Override
    public Repository<?> getPendingState() {
        return aionHub.getPendingState().getRepository();
    }

    @Override
    public Repository<?> getSnapshotTo(byte[] root) {
        Repository<?> repository = aionHub.getRepository();
        Repository<?> snapshot = repository.getSnapshotTo(root);

        return snapshot;
    }

    @Override
    public List<AionTransaction> getWireTransactions() {
        return aionHub.getPendingState().getPendingTransactions();
    }

    @Override
    public List<AionTransaction> getPendingStateTransactions() {
        return aionHub.getPendingState().getPendingTransactions();
    }

    @Override
    public AionHub getAionHub() {
        return aionHub;
    }

    @Override
    public Optional<Long> getLocalBestBlockNumber() {
        try {
            return Optional.of(this.getAionHub().getBlockchain().getBestBlock().getNumber());
        } catch (Exception e) {
            // we may get null pointers here, desire is to isolate
            // the API from these occurances
            LOG_GEN.debug("query request failed ", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> getNetworkBestBlockNumber() {
        try {
            return Optional.of(this.getAionHub().getSyncMgr().getNetworkBestBlockNumber());
        } catch (Exception e) {
            LOG_GEN.debug("query request failed ", e);
            return Optional.empty();
        }
    }

    @Override
    public void setApiServiceCallback(BlockchainCallbackInterface blockchainCallbackForApiServer) {
        blockchainCallbackInterfaces.add(blockchainCallbackForApiServer);
    }

    @Override
    public Optional<Long> getInitialStartingBlockNumber() {
        try {
            return Optional.of(this.aionHub.getStartingBlock().getNumber());
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    // assumes a correctly formatted block number
    public Optional<AccountState> getAccountState(AionAddress address, long blockNumber) {
        try {
            byte[] stateRoot =
                    this.aionHub.getBlockchain().getBlockByNumber(blockNumber).getStateRoot();
            AccountState account =
                    (AccountState)
                            this.aionHub
                                    .getRepository()
                                    .getSnapshotTo(stateRoot)
                                    .getAccountState(address);

            if (account == null) return Optional.empty();

            return Optional.of(account);
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    // assumes a correctly formatted blockHash
    public Optional<AccountState> getAccountState(AionAddress address, byte[] blockHash) {
        try {
            byte[] stateRoot =
                    this.aionHub.getBlockchain().getBlockByHash(blockHash).getStateRoot();
            AccountState account =
                    (AccountState)
                            this.aionHub
                                    .getRepository()
                                    .getSnapshotTo(stateRoot)
                                    .getAccountState(address);

            if (account == null) return Optional.empty();

            return Optional.of(account);
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    public Optional<AccountState> getAccountState(AionAddress address) {
        try {
            byte[] stateRoot = this.aionHub.getBlockchain().getBestBlock().getStateRoot();
            AccountState account =
                    (AccountState)
                            this.aionHub
                                    .getRepository()
                                    .getSnapshotTo(stateRoot)
                                    .getAccountState(address);

            if (account == null) return Optional.empty();

            return Optional.of(account);
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    public Optional<ByteArrayWrapper> getCode(AionAddress address) {
        byte[] code = this.aionHub.getRepository().getCode(address);
        if (code == null) return Optional.empty();
        return Optional.of(ByteArrayWrapper.wrap(code));
    }

    private static class Holder {
        static final AionImpl INSTANCE = new AionImpl(false);
    }

    private static class HolderForTest {
        static final AionImpl INSTANCE = new AionImpl(true);
    }

    public static class PendingTxCallback {
        List<BlockchainCallbackInterface> callbackInterfaces;

        public PendingTxCallback(List<BlockchainCallbackInterface> callbackInterfaces) {
            this.callbackInterfaces = callbackInterfaces;
        }

        public void pendingTxReceivedCallback(List<AionTransaction> newPendingTx) {
            for (BlockchainCallbackInterface callbackInterface : callbackInterfaces) {
                if (callbackInterface.isForApiServer()) {
                    for (AionTransaction tx : newPendingTx) {
                        callbackInterface.pendingTxReceived(tx);
                    }
                }
            }
        }

        public void pendingTxStateUpdateCallback(PendingTxDetails txDetails) {
            for (BlockchainCallbackInterface callbackInterface : callbackInterfaces) {
                if (callbackInterface.isForApiServer()) {
                    callbackInterface.pendingTxUpdated(txDetails);
                }
            }
        }
    }

    public static class TransactionBroadcastCallback {
        IAionChain chainInterface;

        public TransactionBroadcastCallback(IAionChain chainInterface) {
            if (chainInterface == null) {
                throw new NullPointerException();
            }
            this.chainInterface = chainInterface;
        }

        public void broadcastTransactions(List<AionTransaction> transactions) {
            chainInterface.broadcastTransactions(transactions);
        }
    }

    public static class NetworkBestBlockCallback {
        IAionChain chainInterface;

        public NetworkBestBlockCallback(IAionChain chainInterface) {
            if (chainInterface == null) {
                throw new NullPointerException();
            }
            this.chainInterface = chainInterface;
        }

        public long getNetworkBestBlockNumber() {
            Optional<Long> networkBest = chainInterface.getNetworkBestBlockNumber();
            return networkBest.isPresent() ? networkBest.get() : 0;
        }
    }
}
