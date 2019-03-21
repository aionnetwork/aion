package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.zero.impl.AionHub.INIT_ERROR_EXIT_CODE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepository;
import org.aion.mcf.db.TransactionStore;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.trie.TrieImpl;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.p2p.V1Constants;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

/** Has direct database connection. */
public class AionRepositoryImpl
        extends AbstractRepository<AionBlock, A0BlockHeader, AionBlockStore> {

    private TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> transactionStore;

    // pending block store
    private PendingBlockStore pendingStore;

    // inferred contract information not used for consensus
    private ObjectDataSource<ContractInformation> contractInfoSource;

    /**
     * used by getSnapShotTo
     *
     * <p>@ATTENTION: when do snap shot, another instance will be created. Make sure it is used only
     * by getSnapShotTo
     */
    protected AionRepositoryImpl() {}

    protected AionRepositoryImpl(RepositoryConfig repoConfig) {
        this.cfg = repoConfig;
        init();
    }

    public static AionRepositoryImpl inst() {
        return AionRepositoryImplHolder.inst;
    }

    public static AionRepositoryImpl createForTesting(RepositoryConfig repoConfig) {
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
            this.contractInfoSource =
                    new ObjectDataSource<>(
                            contractIndexDatabase, ContractInformation.RLP_SERIALIZER);

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
            Map<Address, AccountState> stateCache, Map<Address, ContractDetails> detailsCache) {
        rwLock.writeLock().lock();

        try {
            for (Map.Entry<Address, AccountState> entry : stateCache.entrySet()) {
                Address address = entry.getKey();
                AccountState accountState = entry.getValue();
                ContractDetails contractDetails = detailsCache.get(address);

                if (accountState.isDeleted()) {
                    // TODO-A: batch operations here
                    try {
                        worldState.delete(address.toBytes());
                    } catch (Exception e) {
                        LOG.error("key deleted exception [{}]", e.toString());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("key deleted <key={}>", Hex.toHexString(address.toBytes()));
                    }
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

            if (LOG.isTraceEnabled()) {
                LOG.trace("updated: detailsCache.size: {}", detailsCache.size());
            }
            stateCache.clear();
            detailsCache.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** @implNote The method calling this method must handle the locking. */
    private void updateContractDetails(
            final Address address, final ContractDetails contractDetails) {
        // locked by calling method
        detailsDS.update(address, contractDetails);
    }

    @Override
    public void flush() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("------ FLUSH ON " + this.toString());
        }
        rwLock.writeLock().lock();
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("flushing to disk");
            }
            long s = System.currentTimeMillis();

            // First sync worldState.
            if (LOG.isInfoEnabled()) {

                LOG.info("worldState.sync()");
            }
            worldState.sync();

            // Flush all necessary caches.
            if (LOG.isInfoEnabled()) {
                LOG.info("flush all databases");
            }

            if (databaseGroup != null) {
                for (ByteArrayKeyValueDatabase db : databaseGroup) {
                    if (!db.isAutoCommitEnabled()) {
                        db.commit();
                    }
                }
            } else {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("databaseGroup is null");
                }
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("RepositoryImpl.flush took " + (System.currentTimeMillis() - s) + " ms");
            }
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
    public RepositoryCache startTracking() {
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
    public ByteArrayWrapper getStorageValue(Address address, ByteArrayWrapper key) {
        ContractDetails details = getContractDetails(address);
        return (details == null) ? null : details.get(key);
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
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(
            Address address, Collection<ByteArrayWrapper> keys) {
        ContractDetails details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public byte[] getCode(Address address) {
        AccountState accountState = getAccountState(address);

        if (accountState == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = accountState.getCodeHash();

        ContractDetails details = getContractDetails(address);
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
     *     returned is a newly created object by {@link ContractDetails#getSnapshotTo(byte[])}.
     *     Since this querying method it locked, the methods calling it <b>may not need to be locked
     *     or synchronized</b>, depending on the specific use case.
     */
    @Override
    public ContractDetails getContractDetails(Address address) {
        rwLock.readLock().lock();

        try {
            ContractDetails details;

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
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "New AccountSate [{}], State [{}]",
                            address.toString(),
                            result.toString());
                }
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
            Map<Address, ContractDetails> cacheDetails) {

        AccountState account = getAccountState(address);
        ContractDetails details = getContractDetails(address);

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
    public Repository getSnapshotTo(byte[] root) {
        rwLock.readLock().lock();

        try {
            AionRepositoryImpl repo = new AionRepositoryImpl();
            repo.blockStore = blockStore;
            repo.contractInfoSource = contractInfoSource;
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
    public ByteArrayKeyValueDatabase getStateDatabase() {
        return this.stateDatabase;
    }

    public ByteArrayKeyValueDatabase getStateArchiveDatabase() {
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
    public ByteArrayKeyValueDatabase getDetailsDatabase() {
        return this.detailsDatabase;
    }

    /** For testing. */
    public ByteArrayKeyValueDatabase getBlockDatabase() {
        return this.blockDatabase;
    }

    /** For testing. */
    public ByteArrayKeyValueDatabase getIndexDatabase() {
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

    /**
     * Calls {@link ByteArrayKeyValueDatabase#drop()} on all the current databases except for the
     * ones given in the list by name.
     *
     * @param names the names of the databases that should not be dropped
     */
    public void dropDatabasesExcept(List<String> names) {
        for (ByteArrayKeyValueDatabase db : databaseGroup) {
            if (!names.contains(db.getName().get())) {
                LOG.warn("Dropping database " + db.toString() + " ...");
                db.drop();
                LOG.warn(db.toString() + " successfully dropped and reopened.");
            }
        }
    }

    @Override
    public void compact() {
        rwLock.writeLock().lock();
        try {
            if (databaseGroup != null) {
                for (ByteArrayKeyValueDatabase db : databaseGroup) {
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

    /**
     * Retrieves the value for a given node from the database associated with the given type.
     *
     * @param key the key of the node to be retrieved
     * @param dbType the database where the key should be found
     * @return the {@code byte} array value associated with the given key or {@code null} when the
     *     key cannot be found in the database.
     * @throws IllegalArgumentException if the given key is null or the database type is not
     *     supported
     */
    public byte[] getTrieNode(byte[] key, DatabaseType dbType) {
        ByteArrayKeyValueDatabase db = selectDatabase(dbType);

        Optional<byte[]> value = db.get(key);
        return value.orElse(null);
    }

    /**
     * Retrieves nodes referenced by a trie node value, where the size of the result is bounded by
     * the given limit.
     *
     * @param value a trie node value which may be referencing other nodes
     * @param limit the maximum number of key-value pairs to be retrieved by this method, which
     *     limits the search in the trie; zero and negative values for the limit will result in no
     *     search and an empty map will be returned
     * @param dbType the database where the value was stored and further keys should be searched for
     * @return an empty map when the value does not reference other trie nodes or the given limit is
     *     invalid, or a map containing all the referenced nodes reached while keeping within the
     *     limit on the result size
     */
    public Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(
            byte[] value, int limit, DatabaseType dbType) {
        if (limit <= 0) {
            return Collections.emptyMap();
        } else {
            ByteArrayKeyValueDatabase db = selectDatabase(dbType);

            Trie trie = new TrieImpl(db);
            return trie.getReferencedTrieNodes(value, limit);
        }
    }

    /**
     * Imports a trie node to the indicated blockchain database.
     *
     * @param key the hash key of the trie node to be imported
     * @param value the value of the trie node to be imported
     * @param dbType the database where the key-value pair should be stored
     * @return a {@link TrieNodeResult} indicating the success or failure of the import operation
     * @throws IllegalArgumentException if the given key is null or the database type is not
     *     supported
     */
    public TrieNodeResult importTrieNode(byte[] key, byte[] value, DatabaseType dbType) {
        // empty keys are not allowed
        if (key == null || key.length != V1Constants.HASH_SIZE) {
            return TrieNodeResult.INVALID_KEY;
        }

        // not allowing deletions to be imported
        if (value == null || value.length == 0) {
            return TrieNodeResult.INVALID_VALUE;
        }

        ByteArrayKeyValueDatabase db = selectDatabase(dbType);

        Optional<byte[]> stored = db.get(key);
        if (stored.isPresent()) {
            if (Arrays.equals(stored.get(), value)) {
                return TrieNodeResult.KNOWN;
            } else {
                return TrieNodeResult.INCONSISTENT;
            }
        }

        db.put(key, value);
        return TrieNodeResult.IMPORTED;
    }

    private ByteArrayKeyValueDatabase selectDatabase(DatabaseType dbType) {
        switch (dbType) {
            case DETAILS:
                return detailsDatabase;
            case STORAGE:
                return storageDatabase;
            case STATE:
                return stateDatabase;
            default:
                throw new IllegalArgumentException(
                        "The database type " + dbType.toString() + " is not supported.");
        }
    }

    /**
     * Returns the {@link ContractInformation} stored for the given contract.
     *
     * @return the {@link ContractInformation} stored for the given contract
     */
    public ContractInformation getIndexedContractInformation(Address contract) {
        return contract == null ? null : contractInfoSource.get(contract.toBytes());
    }

    public void saveIndexedContractInformation(
            Address contract, long inceptionBlock, byte vmUsed, boolean complete) {
        if (contract != null) {
            contractInfoSource.put(
                    contract.toBytes(), new ContractInformation(inceptionBlock, vmUsed, complete));
        }
    }

    public byte getVMUsed(Address contract) {
        ContractInformation ci = getIndexedContractInformation(contract);
        if (ci == null) {
            // defaults to FastVM for backwards compatibility
            return TransactionTypes.FVM_CREATE_CODE;
        } else {
            return ci.getVmUsed();
        }
    }

    private static class AionRepositoryImplHolder {
        // configuration
        private static CfgAion config = CfgAion.inst();

        // repository singleton instance
        private static final AionRepositoryImpl inst =
                new AionRepositoryImpl(
                        new RepositoryConfigImpl(
                                config.getDatabasePath(),
                                ContractDetailsAion.getInstance(),
                                config.getDb()));
    }
}
