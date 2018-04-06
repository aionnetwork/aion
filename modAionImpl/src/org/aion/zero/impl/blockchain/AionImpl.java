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

package org.aion.zero.impl.blockchain;

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.equihash.EquihashMiner;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IPendingStateInternal;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.mine.IMineRunner;
import org.aion.vm.TransactionExecutor;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.tx.TxCollector;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class AionImpl implements IAionChain {

    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());
    private static final Logger LOG_TX = AionLoggerFactory.getLogger(LogEnum.TX.toString());

    public AionHub aionHub;

    private CfgAion cfg;

    static private AionImpl inst;

    private TxCollector collector;


    public static AionImpl inst() {
        if (inst == null) {
            inst = new AionImpl();
        }
        return inst;
    }

    private AionImpl() {
        this.cfg = CfgAion.inst();
        aionHub = new AionHub();
        LOG_GEN.info("<node-started endpoint=p2p://" + cfg.getId() + "@" + cfg.getNet().getP2p().getIp() + ":"
                + cfg.getNet().getP2p().getPort() + ">");

        collector = new TxCollector(this.aionHub.getP2pMgr());
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

        Address minerCoinbase = Address.wrap(this.cfg.getConsensus().getMinerAddress());

        if (minerCoinbase.equals(Address.EMPTY_ADDRESS())) {
            LOG_GEN.info("Miner address is not set");
            return null;
        }

        return EquihashMiner.inst();
    }

    @Override
    public void close() {
        aionHub.close();
    }

    @Override
    public AionTransaction createTransaction(BigInteger nonce, Address to, BigInteger value, byte[] data) {
        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);
        return new AionTransaction(nonceBytes, to, valueBytes, data);
    }

    /**
     * Lock removed, both functions submit to executors, which will enforce
     * their own parallelism, therefore function is thread safe
     */
    @SuppressWarnings("unchecked")
    @Override
    public void broadcastTransaction(AionTransaction transaction) {
        transaction.getEncoded();
        collector.submitTx(transaction);
    }

    public void broadcastTransactions(List<AionTransaction> transaction) {
        for(AionTransaction tx : transaction) {
            tx.getEncoded();
        }
        collector.submitTx(transaction);
    }

    public long estimateTxNrg(AionTransaction tx, IAionBlock block) {

        if (tx.getSignature() == null) {
            tx.sign(ECKeyFac.inst().fromPrivate(new byte[64]));
        }

        IRepositoryCache repository = aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            TransactionExecutor executor = new TransactionExecutor(tx, block, repository, true);
            return executor.execute().getReceipt().getEnergyUsed();
        } finally {
            repository.rollback();
        }
    }

    /**
     * TODO: pretty sure we can just use a static key, verify and implement
     */
    @Override
    public AionTxReceipt callConstant(AionTransaction tx, IAionBlock block) {
        if (tx.getSignature() == null) {
            tx.sign(ECKeyFac.inst().fromPrivate(new byte[64]));
        }

        IRepositoryCache repository = aionHub.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            TransactionExecutor executor = new TransactionExecutor(tx, block, repository, true);
            return executor.execute().getReceipt();
        } finally {
            repository.rollback();
        }
    }

    @Override
    public IRepository getRepository() {
        return aionHub.getRepository();
    }

    @Override
    public IRepository<?, ?, ?> getPendingState() {
        return aionHub.getPendingState().getRepository();
    }

    @Override
    public IRepository<?, ?, ?> getSnapshotTo(byte[] root) {
        IRepository<?, ?, ?> repository = aionHub.getRepository();
        IRepository<?, ?, ?> snapshot = repository.getSnapshotTo(root);

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

    /**
     * Returns whether syncing is completed. Note that this implementation is
     * more of a switch than a guarantee as the syncing system may kick back
     * into action if the network falls behind.
     *
     * @return {@code true} if syncing is completed, {@code false} otherwise
     */
    @Override
    public boolean isSyncComplete() {
        try {
            long localBestBlockNumber = this.getAionHub().getBlockchain().getBestBlock().getNumber();
            long networkBestBlockNumber = this.getAionHub().getSyncMgr().getNetworkBestBlockNumber();
            // to prevent unecessary flopping, consider being within 5 blocks of
            // head to be
            // block propagation and not syncing.
            // NOTE: in the future block propagation may not be tied in with
            // syncing
            return (localBestBlockNumber + 5) < networkBestBlockNumber;
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return false;
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
            byte[] stateRoot = this.aionHub.getBlockStore().getChainBlockByNumber(blockNumber).getStateRoot();
            AccountState account = (AccountState) this.aionHub.getRepository().getSnapshotTo(stateRoot)
                    .getAccountState(address);

            if (account == null)
                return Optional.empty();

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
            byte[] stateRoot = this.aionHub.getBlockchain().getBlockByHash(blockHash).getStateRoot();
            AccountState account = (AccountState) this.aionHub.getRepository().getSnapshotTo(stateRoot)
                    .getAccountState(address);

            if (account == null)
                return Optional.empty();

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
            AccountState account = (AccountState) this.aionHub.getRepository().getSnapshotTo(stateRoot)
                    .getAccountState(address);

            if (account == null)
                return Optional.empty();

            return Optional.of(account);
        } catch (Exception e) {
            LOG_GEN.debug("query request failed", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ByteArrayWrapper> getCode(Address address) {
        byte[] code = this.aionHub.getRepository().getCode(address);
        if (code == null)
            return Optional.empty();
        return Optional.of(new ByteArrayWrapper(code));
    }
}