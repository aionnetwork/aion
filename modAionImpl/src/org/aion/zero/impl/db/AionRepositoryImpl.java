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

package org.aion.zero.impl.db;

import org.aion.base.db.*;
import org.aion.base.type.Address;
import org.aion.base.util.Hex;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepository;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

import java.io.File;
import java.math.BigInteger;
import java.util.*;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * Has direct database connection.
 */
public class AionRepositoryImpl extends AbstractRepository<AionBlock, A0BlockHeader, AionBlockStore> {

    private TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> transactionStore;

    /**
     * used by getSnapShotTo
     *
     * @ATTENTION: when do snap shot, another instance will be created. Make
     *         sure it is used only by getSnapShotTo
     */
    protected AionRepositoryImpl() {
    }

    protected AionRepositoryImpl(IRepositoryConfig repoConfig) {
        this.cfg = repoConfig;
        init();
    }

    private static class AionRepositoryImplHolder {
        // configuration
        private static CfgAion config = CfgAion.inst();
        // repository singleton instance
        private final static AionRepositoryImpl inst = new AionRepositoryImpl(
                new RepositoryConfig(new String[] { config.getDb().getVendor() },
                        // config.getDb().getVendorList() database list
                        config.getDb().getVendor(), // database
                        new File(config.getBasePath(), config.getDb().getPath()).getAbsolutePath(), // db path
                        -1, // config.getDb().getPrune() prune flag
                        ContractDetailsAion.getInstance(), // contract details provider
                        config.getDb().isAutoCommitEnabled(), // if false, flush/commit must be called
                        config.getDb().isDbCacheEnabled(), // caching inside the database
                        config.getDb().isDbCompressionEnabled(), // default database compression
                        config.getDb().isHeapCacheEnabled(), // uses heap caching
                        config.getDb().getMaxHeapCacheSize(), // size of the heap cache
                        config.getDb().isHeapCacheStatsEnabled())); // stats for heap cache
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
            this.transactionStore = new TransactionStore<>(transactionDatabase,
                    AionTransactionStoreSerializer.serializer);

            // Setup block store.
            this.blockStore = new AionBlockStore(indexDatabase, blockDatabase);

            // Setup world trie.
            worldState = createStateTrie();
        } catch (Exception e) { // TODO - If any of the connections failed.
            LOG.error("Unable to initialize repository.", e);
        }
    }

    /**
     * @implNote The transaction store is not locked.
     */
    public TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> getTransactionStore() {
        return this.transactionStore;
    }

    private Trie createStateTrie() {
        return new SecureTrie(stateDSPrune).withPruningEnabled(pruneBlockCount >= 0);
    }

    @Override
    public void updateBatch(Map<Address, AccountState> stateCache,
            Map<Address, IContractDetails<DataWord>> detailsCache) {
        rwLock.writeLock().lock();

        for (Map.Entry<Address, AccountState> entry : stateCache.entrySet()) {
            Address address = entry.getKey();
            AccountState accountState = entry.getValue();
            IContractDetails<DataWord> contractDetails = detailsCache.get(address);

            if (accountState.isDeleted()) {
                // TODO-A: batch operations here
                try {
                    worldState.delete(address.toBytes());
                } catch (Exception e) {
                    LOG.error("key deleted exception [{}]", e.toString());
                } finally {
                    rwLock.writeLock().unlock();
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
                            LOG.trace("update: [{}],nonce: [{}] balance: [{}] [{}]", Hex.toHexString(address.toBytes()),
                                    accountState.getNonce(), accountState.getBalance(), contractDetails.getStorage());
                        }
                    }
                    continue;
                }

                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) contractDetails;
                if (contractDetailsCache.origContract == null) {
                    contractDetailsCache.origContract = this.cfg.contractDetailsImpl();

                    try {
                        contractDetailsCache.origContract.setAddress(address);
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOG.error("contractDetailsCache setAddress exception [{}]", e.toString());
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
                    LOG.trace("update: [{}],nonce: [{}] balance: [{}] [{}]", Hex.toHexString(address.toBytes()),
                            accountState.getNonce(), accountState.getBalance(), contractDetails.getStorage());
                }
            }
        }

        LOG.trace("updated: detailsCache.size: {}", detailsCache.size());
        stateCache.clear();
        detailsCache.clear();
        rwLock.writeLock().unlock();
    }

    private void updateContractDetails(final Address address, final IContractDetails<DataWord> contractDetails) {
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
        boolean valid = worldState.isValidRoot(root);
        rwLock.readLock().unlock();
        return valid;
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
    public DataWord getStorageValue(Address address, DataWord key) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? null : details.get(key);
    }

    @Override
    public int getStorageSize(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? 0 : details.getStorageSize();
    }

    @Override
    public Set<DataWord> getStorageKeys(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptySet() : details.getStorageKeys();
    }

    @Override
    public Map<DataWord, DataWord> getStorage(Address address, Collection<DataWord> keys) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public byte[] getCode(Address address) {
        AccountState accountState = getAccountState(address);

        if (accountState == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = accountState.getCodeHash();

        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? EMPTY_BYTE_ARRAY : details.getCode(codeHash);
    }

    @Override
    public BigInteger getNonce(Address address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getNonce();
    }

    private void updateAccountState(Address address, AccountState accountState) {
        // locked by calling method
        worldState.update(address.toBytes(), accountState.getEncoded());
    }

    /**
     * @inheritDoc
     * @implNote Any other method calling this can rely on the fact that
     *         the contract details returned is a newly created object by {@link IContractDetails#getSnapshotTo(byte[])}.
     *         Since this querying method it locked, the methods calling it
     *         <b>may not need to be locked or synchronized</b>, depending on the specific use case.
     */
    @Override
    public IContractDetails<DataWord> getContractDetails(Address address) {
        rwLock.readLock().lock();

        IContractDetails<DataWord> details = null;

        try {
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
        } finally {
            rwLock.readLock().unlock();
            return details;
        }
    }

    @Override
    public boolean hasContractDetails(Address address) {
        rwLock.readLock().lock();
        boolean hasDetails = detailsDS.get(address.toBytes()) != null;
        rwLock.readLock().unlock();

        return hasDetails;
    }

    /**
     * @inheritDoc
     * @implNote Any other method calling this can rely on the fact that
     *         the account state returned is a newly created object.
     *         Since this querying method it locked, the methods calling it
     *         <b>may not need to be locked or synchronized</b>, depending on the specific use case.
     */
    @Override
    public AccountState getAccountState(Address address) {
        rwLock.readLock().lock();

        AccountState result = null;

        try {
            byte[] accountData = worldState.get(address.toBytes());

            if (accountData.length != 0) {
                result = new AccountState(accountData);
                LOG.debug("New AccountSate [{}], State [{}]", address.toString(), result.toString());
            }
        } finally {
            rwLock.readLock().unlock();
            return result;
        }
    }

    @Override
    public boolean hasAccountState(Address address) {
        return getAccountState(address) != null;
    }

    /**
     * @implNote The loaded objects are fresh copies of the original account
     *         state and contract details.
     */
    @Override
    public void loadAccountState(Address address, Map<Address, AccountState> cacheAccounts,
            Map<Address, IContractDetails<DataWord>> cacheDetails) {

        AccountState account = getAccountState(address);
        IContractDetails<DataWord> details = getContractDetails(address);

        account = (account == null) ? new AccountState() : new AccountState(account);
        details = new ContractDetailsCacheImpl(details);
        // details.setAddress(addr);

        cacheAccounts.put(address, account);
        cacheDetails.put(address, details);
    }

    @Override
    public byte[] getRoot() {
        rwLock.readLock().lock();
        byte[] root = worldState.getRootHash();
        rwLock.readLock().unlock();
        return root;
    }

    public void setRoot(byte[] root) {
        rwLock.writeLock().lock();
        worldState.setRoot(root);
        rwLock.writeLock().unlock();
    }

    public void setPruneBlockCount(long pruneBlockCount) {
        rwLock.writeLock().lock();
        this.pruneBlockCount = pruneBlockCount;
        rwLock.writeLock().unlock();
    }

    public void commitBlock(A0BlockHeader blockHeader) {
        rwLock.writeLock().lock();

        worldState.sync();
        detailsDS.syncLargeStorage();

        if (pruneBlockCount >= 0) {
            stateDSPrune.storeBlockChanges(blockHeader);
            detailsDS.getStorageDSPrune().storeBlockChanges(blockHeader);
            pruneBlocks(blockHeader);
        }
        rwLock.writeLock().unlock();
    }

    private void pruneBlocks(A0BlockHeader curBlock) {
        if (curBlock.getNumber() > bestBlockNumber) { // pruning only on
            // increasing blocks
            long pruneBlockNumber = curBlock.getNumber() - pruneBlockCount;
            if (pruneBlockNumber >= 0) {
                byte[] pruneBlockHash = blockStore.getBlockHashByNumber(pruneBlockNumber);
                if (pruneBlockHash != null) {
                    A0BlockHeader header = blockStore.getBlockByHash(pruneBlockHash).getHeader();
                    stateDSPrune.prune(header);
                    detailsDS.getStorageDSPrune().prune(header);
                }
            }
        }
        bestBlockNumber = curBlock.getNumber();
    }

    public Trie getWorldState() {
        return worldState;
    }

    @Override
    public IRepository getSnapshotTo(byte[] root) {
        rwLock.readLock().lock();

        AionRepositoryImpl repo = new AionRepositoryImpl();
        repo.blockStore = blockStore;
        repo.cfg = cfg;
        repo.stateDatabase = this.stateDatabase;
        repo.stateDSPrune = this.stateDSPrune;
        repo.pruneBlockCount = this.pruneBlockCount;
        repo.detailsDS = this.detailsDS;
        repo.isSnapshot = true;

        repo.worldState = repo.createStateTrie();
        repo.worldState.setRoot(root);

        rwLock.readLock().unlock();

        return repo;
    }

    /**
     * This function cannot for any reason fail, otherwise we may have dangling
     * file IO locks
     */
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
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves the underlying state database that sits below all caches. This
     * is usually provided by {@link org.aion.db.impl.leveldb.LevelDB} or
     * {@link org.aion.db.impl.leveldb.LevelDB}.
     * <p>
     * Note that referencing the state database directly is unsafe, and should
     * only be used for debugging and testing purposes.
     *
     * @return
     */
    public IByteArrayKeyValueDatabase getStateDatabase() {
        return this.stateDatabase;
    }

    /**
     * Retrieves the underlying details database that sits below all caches.
     * This is usually provided by {@link org.aion.db.impl.mockdb.MockDB}
     * or {@link org.aion.db.impl.mockdb.MockDB}.
     * <p>
     * Note that referencing the state database directly is unsafe, and should
     * only be used for debugging and testing purposes.
     *
     * @return
     */
    public IByteArrayKeyValueDatabase getDetailsDatabase() {
        return this.detailsDatabase;
    }

    @Override
    public String toString() {
        return "AionRepositoryImpl{ identityHashCode=" + System.identityHashCode(this) + ", " + //
                "databaseGroupSize=" + (databaseGroup == null ? 0 : databaseGroup.size()) + '}';
    }

    @Override
    public void compact() {
        rwLock.writeLock().lock();
        if (databaseGroup != null) {
            for (IByteArrayKeyValueDatabase db : databaseGroup) {
                db.compact();
            }
        } else {
            LOG.error("Database group is null.");
        }
        rwLock.writeLock().unlock();
    }
}
