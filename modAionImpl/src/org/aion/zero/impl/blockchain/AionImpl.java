package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.equihash.EquihashMiner;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.mine.IMineRunner;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.BulkExecutor;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.tx.TxCollector;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

public class AionImpl implements IAionChain {

    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());
    private static final Logger LOG_TX = AionLoggerFactory.getLogger(LogEnum.TX.toString());
    private static final Logger LOG_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final ECKey keyForCallandEstimate = ECKeyFac.inst().fromPrivate(new byte[64]);

    public AionHub aionHub;

    private CfgAion cfg;

    private TxCollector collector;

    private AionImpl() {
        this.cfg = CfgAion.inst();
        aionHub = new AionHub();
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

    @Override
    public IPowChain<AionBlock, A0BlockHeader> getBlockchain() {
        return aionHub.getBlockchain();
    }

    public synchronized ImportResult addNewMinedBlock(AionBlock block) {
        ImportResult importResult = this.aionHub.getBlockchain().tryToConnect(block);

        if (importResult == ImportResult.IMPORTED_BEST) {
            this.aionHub.getPropHandler().propagateNewBlock(block);
        }
        return importResult;
    }

    @Override
    public IMineRunner getBlockMiner() {

        try {
            Address.wrap(this.cfg.getConsensus().getMinerAddress());
            return EquihashMiner.inst();
        } catch (Exception e) {
            LOG_GEN.info("Miner address is not set");
            return null;
        }
    }

    @Override
    public void close() {
        aionHub.close();
    }

    @Override
    public AionTransaction createTransaction(
            BigInteger nonce, Address to, BigInteger value, byte[] data) {
        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);
        return new AionTransaction(nonceBytes, to, valueBytes, data);
    }

    /**
     * Lock removed, both functions submit to executors, which will enforce their own parallelism,
     * therefore function is thread safe
     */
    @SuppressWarnings("unchecked")
    @Override
    public void broadcastTransaction(AionTransaction transaction) {
        transaction.getEncoded();
        collector.submitTx(transaction);
    }

    public void broadcastTransactions(List<AionTransaction> transaction) {
        for (AionTransaction tx : transaction) {
            tx.getEncoded();
        }
        collector.submitTx(transaction);
    }

    public long estimateTxNrg(AionTransaction tx, IAionBlock block) {

        if (tx.getSignature() == null) {
            tx.sign(keyForCallandEstimate);
        }

        RepositoryCache repository =
                aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = true;
            boolean incrementSenderNonce = true;
            boolean fork040enabled = false;
            boolean checkBlockEnergyLimit = false;

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block,
                tx,
                repository,
                isLocalCall,
                incrementSenderNonce,
                fork040enabled,
                checkBlockEnergyLimit,
                LOG_VM).getReceipt().getEnergyUsed();
        } catch (VMException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(-1);
            return 0;
        } finally {
            repository.rollback();
        }
    }

    @Override
    public AionTxReceipt callConstant(AionTransaction tx, IAionBlock block) {

        if (tx.getSignature() == null) {
            tx.sign(keyForCallandEstimate);
        }

        RepositoryCache repository =
                aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = true;
            boolean incrementSenderNonce = true;
            boolean fork040enabled = false;
            boolean checkBlockEnergyLimit = false;

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block,
                tx,
                repository,
                isLocalCall,
                incrementSenderNonce,
                fork040enabled,
                checkBlockEnergyLimit,
                LOG_VM).getReceipt();
        } catch (VMException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(-1);
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
    public Repository<?, ?> getPendingState() {
        return aionHub.getPendingState().getRepository();
    }

    @Override
    public Repository<?, ?> getSnapshotTo(byte[] root) {
        Repository<?, ?> repository = aionHub.getRepository();
        Repository<?, ?> snapshot = repository.getSnapshotTo(root);

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
    public void exitOn(long number) {
        aionHub.getBlockchain().setExitOn(number);
    }

    @Override
    public AionHub getAionHub() {
        return aionHub;
    }

    private IPendingStateInternal<?, ?> getIPendingStateInternal() {
        return this.aionHub.getPendingState();
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
    public Optional<Long> getInitialStartingBlockNumber() {
        try {
            return Optional.of(this.aionHub.getStartingBlock().getNumber());
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    // assumes a correctly formatted block number
    @Override
    public Optional<AccountState> getAccountState(Address address, long blockNumber) {
        try {
            byte[] stateRoot =
                    this.aionHub.getBlockStore().getChainBlockByNumber(blockNumber).getStateRoot();
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
    @Override
    public Optional<AccountState> getAccountState(Address address, byte[] blockHash) {
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

    @Override
    public Optional<AccountState> getAccountState(Address address) {
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

    @Override
    public Optional<ByteArrayWrapper> getCode(Address address) {
        byte[] code = this.aionHub.getRepository().getCode(address);
        if (code == null) return Optional.empty();
        return Optional.of(new ByteArrayWrapper(code));
    }

    private static class Holder {
        static final AionImpl INSTANCE = new AionImpl();
    }
}
