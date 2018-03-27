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
import org.aion.db.impl.AbstractDatabaseWithCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepository;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.trie.Trie;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.*;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.aion.mcf.vm.types.DataWord;

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
     *             sure it is used only by getSnapShotTo
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
                        config.getDb().getVendor(),
                        new File(config.getBasePath(), config.getDb().getPath()).getAbsolutePath(),
                        -1,
                        ContractDetailsAion.getInstance(),
                        config.getDb().isAutoCommitEnabled(),
                        config.getDb().isDbCacheEnabled(),
                        config.getDb().isDbCompressionEnabled(),
                        config.getDb().isHeapCacheEnabled(),
                        config.getDb().getMaxHeapCacheSize(),
                        config.getDb().isHeapCacheStatsEnabled(),
                        config.getDb().getFdOpenAllocSize(),
                        config.getDb().getBlockSize(),
                        config.getDb().getWriteBufferSize(),
                        config.getDb().getCacheSize()));
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
            // TODO
            this.blockStore = new AionBlockStore(indexDatabase, blockDatabase);

            // Setup world trie.
            worldState = createStateTrie();
        } catch (Exception e) { // TODO - If any of the connections failed.
            e.printStackTrace();
        }
    }

    public TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> getTransactionStore() {
        return this.transactionStore;
    }

    private Trie createStateTrie() {
        return new SecureTrie(stateDSPrune).withPruningEnabled(pruneBlockCount >= 0);
    }

    @Override
    public synchronized void updateBatch(Map<Address, AccountState> stateCache,
            Map<Address, IContractDetails<DataWord>> detailsCache) {

        for (Map.Entry<Address, AccountState> entry : stateCache.entrySet()) {
            Address address = entry.getKey();
            AccountState accountState = entry.getValue();
            IContractDetails<DataWord> contractDetails = detailsCache.get(address);

            if (accountState.isDeleted()) {
                // TODO-A: batch operations here
                rwLock.readLock().lock();
                try {
                    worldState.delete(address.toBytes());
                } catch (Exception e) {
                    LOG.error("key deleted exception [{}]", e.toString());
                } finally {
                    rwLock.readLock().unlock();
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
    }

    private synchronized void updateContractDetails(final Address address,
            final IContractDetails<DataWord> contractDetails) {
        rwLock.readLock().lock();
        try {
            detailsDS.update(address, contractDetails);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        LOG.debug("------ FLUSH ON " + this.toString());
        LOG.debug("rwLock.writeLock().lock()");
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
                    if (db instanceof AbstractDatabaseWithCache) {
                        // printing heap cache stats when enabled
                        AbstractDatabaseWithCache dbwc = (AbstractDatabaseWithCache) db;
                        if (dbwc.isStatsEnabled()) {
                            LOG.debug(dbwc.getName().get() + ": " + dbwc.getStats().toString());
                        }
                    }
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
        return worldState.isValidRoot(root);
    }

    @Override
    public synchronized void syncToRoot(final byte[] root) {
        rwLock.readLock().lock();
        try {
            worldState.setRoot(root);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public synchronized IRepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    // @Override
    public synchronized void dumpState(IAionBlock block, long nrgUsed, int txNumber, byte[] txHash) {
        return;

    }

    public synchronized String getTrieDump() {
        rwLock.readLock().lock();
        try {
            return worldState.getTrieDump();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public synchronized void dumpTrie(IAionBlock block) {
        return;

    }

    @Override
    public synchronized BigInteger getBalance(Address address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getBalance();
    }

    @Override
    public synchronized DataWord getStorageValue(Address address, DataWord key) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? null : details.get(key);
    }

    @Override
    public synchronized int getStorageSize(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? 0 : details.getStorageSize();
    }

    @Override
    public synchronized Set<DataWord> getStorageKeys(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptySet() : details.getStorageKeys();
    }

    @Override
    public synchronized Map<DataWord, DataWord> getStorage(Address address, Collection<DataWord> keys) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public synchronized byte[] getCode(Address address) {
        AccountState accountState = getAccountState(address);

        if (accountState == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = accountState.getCodeHash();

        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? EMPTY_BYTE_ARRAY : details.getCode(codeHash);
    }

    @Override
    public synchronized BigInteger getNonce(Address address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getNonce();
    }

    private synchronized void updateAccountState(Address address, AccountState accountState) {
        rwLock.readLock().lock();
        try {
            worldState.update(address.toBytes(), accountState.getEncoded());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public synchronized IContractDetails<DataWord> getContractDetails(Address address) {
        rwLock.readLock().lock();
        try {
            // That part is important cause if we have
            // to sync details storage according the trie root
            // saved in the account
            AccountState accountState = getAccountState(address);
            byte[] storageRoot = EMPTY_TRIE_HASH;
            if (accountState != null) {
                storageRoot = getAccountState(address).getStateRoot();
            }
            IContractDetails<DataWord> details = detailsDS.get(address.toBytes());

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
        return detailsDS.get(address.toBytes()) != null;
    }

    @Override
    public synchronized AccountState getAccountState(Address address) {
        // TODO
        rwLock.readLock().lock();
        try {
            AccountState result = null;
            byte[] accountData = worldState.get(address.toBytes());

            if (accountData.length != 0) {
                result = new AccountState(accountData);
                LOG.debug("New AccountSate [{}], State [{}]", address.toString(), result.toString());
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
     * @implNote The loaded objects are fresh copies of the original account
     *           state and contract details.
     */
    @Override
    public synchronized void loadAccountState(Address address, Map<Address, AccountState> cacheAccounts,
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
    public synchronized byte[] getRoot() {
        return worldState.getRootHash();
    }

    public synchronized void setRoot(byte[] root) {
        worldState.setRoot(root);
    }

    public void setPruneBlockCount(long pruneBlockCount) {
        this.pruneBlockCount = pruneBlockCount;
    }

    public synchronized void commitBlock(A0BlockHeader blockHeader) {
        worldState.sync();
        detailsDS.syncLargeStorage();

        if (pruneBlockCount >= 0) {
            stateDSPrune.storeBlockChanges(blockHeader);
            detailsDS.getStorageDSPrune().storeBlockChanges(blockHeader);
            pruneBlocks(blockHeader);
        }
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
    public synchronized IRepository getSnapshotTo(byte[] root) {

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
        if (databaseGroup != null) {
            for (IByteArrayKeyValueDatabase db : databaseGroup) {
                db.compact();
            }
        } else {
            LOG.error("Database group is null.");
        }
    }
}
