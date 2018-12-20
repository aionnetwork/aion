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

package org.aion.zero.impl.db;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.zero.impl.AionHub.INIT_ERROR_EXIT_CODE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.util.Hex;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepository;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.trie.Trie;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

/** Has direct database connection. */
public class AionRepositoryImpl
        extends AbstractRepository<AionBlock, A0BlockHeader, AionBlockStore> {

    private TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> transactionStore;

    // pending block store
    private PendingBlockStore pendingStore;

    /**
     * used by getSnapShotTo
     *
     * <p>@ATTENTION: when do snap shot, another instance will be created. Make sure it is used only
     * by getSnapShotTo
     */
    protected AionRepositoryImpl() {}

    protected AionRepositoryImpl(IRepositoryConfig repoConfig) {
        this.cfg = repoConfig;
        init();
    }

    private static class AionRepositoryImplHolder {
        // configuration
        private static CfgAion config = CfgAion.inst();

        // repository singleton instance
        private static final AionRepositoryImpl inst =
                new AionRepositoryImpl(
                        new RepositoryConfig(
                                config.getDatabasePath(),
                                ContractDetailsAion.getInstance(),
                                config.getDb()));
    }

    public static AionRepositoryImpl inst() {
        return AionRepositoryImplHolder.inst;
    }

    public static AionRepositoryImpl createForTesting(IRepositoryConfig repoConfig) {
        return new AionRepositoryImpl(repoConfig);
    }

    private void init() {
        try {
            initializeDatabasesAndCaches();

            // Setup the cache for transaction data source.
            this.transactionStore =
                    new TransactionStore<>(
                            transactionDatabase, AionTransactionStoreSerializer.serializer);

            // Setup block store.
            this.blockStore = new AionBlockStore(indexDatabase, blockDatabase, checkIntegrity);

            this.pendingStore = new PendingBlockStore(pendingStoreProperties);

            // Setup world trie.
            worldState = createStateTrie();
        } catch (Exception e) {
            LOGGEN.error("Shutdown due to failure to initialize repository.");
            // the above message does not get logged without the printStackTrace below
            e.printStackTrace();
            System.exit(INIT_ERROR_EXIT_CODE);
        }
    }

    public PendingBlockStore getPendingBlockStore() {
        return this.pendingStore;
    }

    /** @implNote The transaction store is not locked within the repository implementation. */
    public TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> getTransactionStore() {
        return this.transactionStore;
    }

    private Trie createStateTrie() {
        return new SecureTrie(stateDSPrune).withPruningEnabled(pruneEnabled);
    }

    @Override
    public void updateBatch(
            Map<Address, AccountState> stateCache,
            Map<Address, IContractDetails<IDataWord>> detailsCache) {
        rwLock.writeLock().lock();

        try {
            for (Map.Entry<Address, AccountState> entry : stateCache.entrySet()) {
                Address address = entry.getKey();
                AccountState accountState = entry.getValue();
                IContractDetails<IDataWord> contractDetails = detailsCache.get(address);

                if (accountState.isDeleted()) {
                    // TODO-A: batch operations here
                    try {
                        worldState.delete(address.toBytes());
                    } catch (Exception e) {
                        LOG.error("key deleted exception [{}]", e.toString());
                    }
                    LOG.debug("key deleted <key={}>", Hex.toHexString(address.toBytes()));
                } else {

                    if (!contractDetails.isDirty()) {
                        // code added because contract details are not reliably
                        // marked as dirty at present
                        // TODO: issue above will be solved with the conversion to a
                        // ContractState class
                        if (accountState.isDirty()) {
                            updateAccountState(address, accountState);

                            if (LOG.isTraceEnabled()) {
                                LOG.trace(
                                        "update: [{}],nonce: [{}] balance: [{}] [{}]",
                                        Hex.toHexString(address.toBytes()),
                                        accountState.getNonce(),
                                        accountState.getBalance(),
                                        Hex.toHexString(contractDetails.getStorageHash()));
                            }
                        }
                        continue;
                    }

                    ContractDetailsCacheImpl contractDetailsCache =
                            (ContractDetailsCacheImpl) contractDetails;
                    if (contractDetailsCache.origContract == null) {
                        contractDetailsCache.origContract = this.cfg.contractDetailsImpl();

                        try {
                            contractDetailsCache.origContract.setAddress(address);
                        } catch (Exception e) {
                            e.printStackTrace();
                            LOG.error(
                                    "contractDetailsCache setAddress exception [{}]", e.toString());
                        }

                        contractDetailsCache.commit();
                    }

                    contractDetails = contractDetailsCache.origContract;

                    updateContractDetails(address, contractDetails);

                    if (!Arrays.equals(accountState.getCodeHash(), EMPTY_TRIE_HASH)) {
                        accountState.setStateRoot(contractDetails.getStorageHash());
                    }

                    updateAccountState(address, accountState);

                    if (LOG.isTraceEnabled()) {
                        LOG.trace(
                                "update: [{}],nonce: [{}] balance: [{}] [{}]",
                                Hex.toHexString(address.toBytes()),
                                accountState.getNonce(),
                                accountState.getBalance(),
                                Hex.toHexString(contractDetails.getStorageHash()));
                    }
                }
            }

            LOG.trace("updated: detailsCache.size: {}", detailsCache.size());
            stateCache.clear();
            detailsCache.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** @implNote The method calling this method must handle the locking. */
    private void updateContractDetails(
            final Address address, final IContractDetails<IDataWord> contractDetails) {
        // locked by calling method
        detailsDS.update(address, contractDetails);
    }

    @Override
    public void flush() {
        LOG.debug("------ FLUSH ON " + this.toString());
        rwLock.writeLock().lock();
        try {
            LOG.debug("flushing to disk");
            long s = System.currentTimeMillis();

            // First sync worldState.
            LOG.info("worldState.sync()");
            worldState.sync();

            // Flush all necessary caches.
            LOG.info("flush all databases");

            if (databaseGroup != null) {
                for (IByteArrayKeyValueDatabase db : databaseGroup) {
                    if (!db.isAutoCommitEnabled()) {
                        db.commit();
                    }
                }
            } else {
                LOG.warn("databaseGroup is null");
            }

            LOG.info("RepositoryImpl.flush took " + (System.currentTimeMillis() - s) + " ms");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void rollback() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValidRoot(byte[] root) {
        rwLock.readLock().lock();
        try {
            return worldState.isValidRoot(root);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean isIndexed(byte[] hash, long level) {
        rwLock.readLock().lock();
        try {
            return blockStore.isIndexed(hash, level);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void syncToRoot(final byte[] root) {
        rwLock.writeLock().lock();
        try {
            worldState.setRoot(root);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public IRepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    public String getTrieDump() {
        rwLock.readLock().lock();
        try {
            return worldState.getTrieDump();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public BigInteger getBalance(Address address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getBalance();
    }

    @Override
    public IDataWord getStorageValue(Address address, IDataWord key) {
        IContractDetails<IDataWord> details = getContractDetails(address);
        IDataWord value = (details == null) ? null : details.get(key);
        if (value == null) {
            return null;
        }
        return (value.isZero()) ? null : value;
    }

    @Override
    public List<byte[]> getPoolTx() {

        List<byte[]> rtn = new ArrayList<>();
        rwLock.readLock().lock();
        try {
            Iterator<byte[]> iterator = txPoolDatabase.keys();
            while (iterator.hasNext()) {
                byte[] b = iterator.next();
                if (txPoolDatabase.get(b).isPresent()) {
                    rtn.add(txPoolDatabase.get(b).get());
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }

        return rtn;
    }

    @Override
    public List<byte[]> getCacheTx() {

        List<byte[]> rtn = new ArrayList<>();
        rwLock.readLock().lock();
        try {
            Iterator<byte[]> iterator = pendingTxCacheDatabase.keys();
            while (iterator.hasNext()) {
                byte[] b = iterator.next();
                if (pendingTxCacheDatabase.get(b).isPresent()) {
                    rtn.add(pendingTxCacheDatabase.get(b).get());
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }

        return rtn;
    }

    @Override
    public Map<IDataWord, IDataWord> getStorage(Address address, Collection<IDataWord> keys) {
        IContractDetails<IDataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public byte[] getCode(Address address) {
        AccountState accountState = getAccountState(address);

        if (accountState == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = accountState.getCodeHash();

        IContractDetails<IDataWord> details = getContractDetails(address);
        return (details == null) ? EMPTY_BYTE_ARRAY : details.getCode(codeHash);
    }

    @Override
    public BigInteger getNonce(Address address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getNonce();
    }

    /** @implNote The method calling this method must handle the locking. */
    private void updateAccountState(Address address, AccountState accountState) {
        // locked by calling method
        worldState.update(address.toBytes(), accountState.getEncoded());
    }

    /**
     * @inheritDoc
     * @implNote Any other method calling this can rely on the fact that the contract details
     *     returned is a newly created object by {@link IContractDetails#getSnapshotTo(byte[])}.
     *     Since this querying method it locked, the methods calling it <b>may not need to be locked
     *     or synchronized</b>, depending on the specific use case.
     */
    @Override
    public IContractDetails<IDataWord> getContractDetails(Address address) {
        rwLock.readLock().lock();

        try {
            IContractDetails<IDataWord> details;

            // That part is important cause if we have
            // to sync details storage according the trie root
            // saved in the account
            AccountState accountState = getAccountState(address);
            byte[] storageRoot = EMPTY_TRIE_HASH;
            if (accountState != null) {
                storageRoot = getAccountState(address).getStateRoot();
            }

            details = detailsDS.get(address.toBytes());

            if (details != null) {
                details = details.getSnapshotTo(storageRoot);
            }
            return details;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean hasContractDetails(Address address) {
        rwLock.readLock().lock();
        try {
            return detailsDS.get(address.toBytes()) != null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * @inheritDoc
     * @implNote Any other method calling this can rely on the fact that the account state returned
     *     is a newly created object. Since this querying method it locked, the methods calling it
     *     <b>may not need to be locked or synchronized</b>, depending on the specific use case.
     */
    @Override
    public AccountState getAccountState(Address address) {
        rwLock.readLock().lock();

        AccountState result = null;

        try {
            byte[] accountData = worldState.get(address.toBytes());

            if (accountData.length != 0) {
                result = new AccountState(accountData);
                LOG.debug(
                        "New AccountSate [{}], State [{}]", address.toString(), result.toString());
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean hasAccountState(Address address) {
        return getAccountState(address) != null;
    }

    /**
     * @implNote The loaded objects are fresh copies of the original account state and contract
     *     details.
     */
    @Override
    public void loadAccountState(
            Address address,
            Map<Address, AccountState> cacheAccounts,
            Map<Address, IContractDetails<IDataWord>> cacheDetails) {

        AccountState account = getAccountState(address);
        IContractDetails<IDataWord> details = getContractDetails(address);

        account = (account == null) ? new AccountState() : new AccountState(account);
        details = new ContractDetailsCacheImpl(details);
        // details.setAddress(addr);

        cacheAccounts.put(address, account);
        cacheDetails.put(address, details);
    }

    @Override
    public byte[] getRoot() {
        rwLock.readLock().lock();
        try {
            return worldState.getRootHash();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void setRoot(byte[] root) {
        rwLock.writeLock().lock();
        try {
            worldState.setRoot(root);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public long getPruneBlockCount() {
        return this.pruneBlockCount;
    }

    public void commitBlock(A0BlockHeader blockHeader) {
        rwLock.writeLock().lock();

        try {
            worldState.sync();
            detailsDS.syncLargeStorage();

            if (pruneEnabled) {
                if (stateDSPrune.isArchiveEnabled() && blockHeader.getNumber() % archiveRate == 0) {
                    // archive block
                    worldState.saveDiffStateToDatabase(
                            blockHeader.getStateRoot(), stateDSPrune.getArchiveSource());
                }
                stateDSPrune.storeBlockChanges(blockHeader.getHash(), blockHeader.getNumber());
                detailsDS
                        .getStorageDSPrune()
                        .storeBlockChanges(blockHeader.getHash(), blockHeader.getNumber());
                pruneBlocks(blockHeader);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void pruneBlocks(A0BlockHeader curBlock) {
        if (curBlock.getNumber() > bestBlockNumber) {
            // pruning only on increasing blocks
            long pruneBlockNumber = curBlock.getNumber() - pruneBlockCount;
            if (pruneBlockNumber >= 0) {
                byte[] pruneBlockHash = blockStore.getBlockHashByNumber(pruneBlockNumber);
                if (pruneBlockHash != null) {
                    A0BlockHeader header = blockStore.getBlockByHash(pruneBlockHash).getHeader();
                    stateDSPrune.prune(header.getHash(), header.getNumber());
                    detailsDS.getStorageDSPrune().prune(header.getHash(), header.getNumber());
                }
            }
        }
        bestBlockNumber = curBlock.getNumber();
    }

    /**
     * @return {@code true} when pruning is enabled and archiving is disabled, {@code false}
     *     otherwise
     */
    public boolean usesTopPruning() {
        return pruneEnabled && !stateDSPrune.isArchiveEnabled();
    }

    public Trie getWorldState() {
        return worldState;
    }

    @Override
    public IRepository getSnapshotTo(byte[] root) {
        rwLock.readLock().lock();

        try {
            AionRepositoryImpl repo = new AionRepositoryImpl();
            repo.blockStore = blockStore;
            repo.cfg = cfg;
            repo.stateDatabase = this.stateDatabase;
            repo.stateWithArchive = this.stateWithArchive;
            repo.stateDSPrune = this.stateDSPrune;

            // pruning config
            repo.pruneEnabled = this.pruneEnabled;
            repo.pruneBlockCount = this.pruneBlockCount;
            repo.archiveRate = this.archiveRate;

            repo.detailsDS = this.detailsDS;
            repo.isSnapshot = true;

            repo.worldState = repo.createStateTrie();
            repo.worldState.setRoot(root);

            // gives snapshots access to the pending store
            repo.pendingStore = this.pendingStore;

            return repo;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool) {

        if (pendingTx.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (isPool) {
                txPoolDatabase.putBatch(pendingTx);
            } else {
                pendingTxCacheDatabase.putBatch(pendingTx);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeTxBatch(Set<byte[]> clearTxSet, boolean isPool) {

        if (clearTxSet.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (isPool) {
                txPoolDatabase.deleteBatch(clearTxSet);
            } else {
                pendingTxCacheDatabase.deleteBatch(clearTxSet);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** This function cannot for any reason fail, otherwise we may have dangling file IO locks */
    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            try {
                if (detailsDS != null) {
                    detailsDS.close();
                    LOGGEN.info("Details data source closed.");
                    detailsDS = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the details data source.", e);
            }

            try {
                if (stateDatabase != null) {
                    stateDatabase.close();
                    LOGGEN.info("State database closed.");
                    stateDatabase = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the state database.", e);
            }

            try {
                if (stateArchiveDatabase != null) {
                    stateArchiveDatabase.close();
                    LOGGEN.info("State archive database closed.");
                    stateArchiveDatabase = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the state archive database.", e);
            }

            try {
                if (transactionDatabase != null) {
                    transactionDatabase.close();
                    LOGGEN.info("Transaction database closed.");
                    transactionDatabase = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the transaction database.", e);
            }

            try {
                if (blockStore != null) {
                    blockStore.close();
                    LOGGEN.info("Block store closed.");
                    blockStore = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the block store.", e);
            }

            try {
                if (pendingStore != null) {
                    pendingStore.close();
                    LOGGEN.info("Pending block store closed.");
                    pendingStore = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the pending block store.", e);
            }

            try {
                if (txPoolDatabase != null) {
                    txPoolDatabase.close();
                    LOGGEN.info("txPoolDatabase store closed.");
                    txPoolDatabase = null;
                }
            } catch (Exception e) {
                LOGGEN.error("Exception occurred while closing the txPoolDatabase store.", e);
            }

            try {
                if (pendingTxCacheDatabase != null) {
                    pendingTxCacheDatabase.close();
                    LOGGEN.info("pendingTxCacheDatabase store closed.");
                    pendingTxCacheDatabase = null;
                }
            } catch (Exception e) {
                LOGGEN.error(
                        "Exception occurred while closing the pendingTxCacheDatabase store.", e);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves the underlying state database that sits below all caches. This is usually provided
     * by {@link org.aion.db.impl.leveldb.LevelDB} or {@link org.aion.db.impl.leveldb.LevelDB}.
     *
     * <p>Note that referencing the state database directly is unsafe, and should only be used for
     * debugging and testing purposes.
     *
     * @return
     */
    public IByteArrayKeyValueDatabase getStateDatabase() {
        return this.stateDatabase;
    }

    public IByteArrayKeyValueDatabase getStateArchiveDatabase() {
        return this.stateArchiveDatabase;
    }

    /**
     * Retrieves the underlying details database that sits below all caches. This is usually
     * provided by {@link org.aion.db.impl.mockdb.MockDB} or {@link org.aion.db.impl.mockdb.MockDB}.
     *
     * <p>Note that referencing the state database directly is unsafe, and should only be used for
     * debugging and testing purposes.
     *
     * @return
     */
    public IByteArrayKeyValueDatabase getDetailsDatabase() {
        return this.detailsDatabase;
    }

    /** For testing. */
    public IByteArrayKeyValueDatabase getBlockDatabase() {
        return this.blockDatabase;
    }

    /** For testing. */
    public IByteArrayKeyValueDatabase getIndexDatabase() {
        return this.indexDatabase;
    }

    @Override
    public String toString() {
        return "AionRepositoryImpl{ identityHashCode="
                + System.identityHashCode(this)
                + ", snapshot: "
                + this.isSnapshot()
                + ", databaseGroupSize="
                + (databaseGroup == null ? 0 : databaseGroup.size())
                + '}';
    }

    @Override
    public void compact() {
        rwLock.writeLock().lock();
        try {
            if (databaseGroup != null) {
                for (IByteArrayKeyValueDatabase db : databaseGroup) {
                    db.compact();
                }
            } else {
                LOG.error("Database group is null.");
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void compactState() {
        rwLock.writeLock().lock();
        try {
            this.stateDatabase.compact();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
