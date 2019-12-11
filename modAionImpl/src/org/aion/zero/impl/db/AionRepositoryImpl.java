package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aion.base.ConstantUtil;
import org.aion.base.AccountState;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Stores;
import org.aion.db.store.XorDataSource;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.TransformedCodeInfo;
import org.aion.zero.impl.trie.SecureTrie;
import org.aion.zero.impl.trie.Trie;
import org.aion.zero.impl.trie.TrieImpl;
import org.aion.zero.impl.trie.TrieNodeResult;
import org.aion.p2p.V1Constants;
import org.aion.precompiled.ContractInfo;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.sync.DatabaseType;
import org.apache.commons.lang3.tuple.Pair;

/** Has direct database connection. */
public class AionRepositoryImpl extends AbstractRepository {

    private TransactionStore transactionStore;

    // pending block store
    private PendingBlockStore pendingStore;

    // inferred contract information not used for consensus
    private ObjectStore<ContractInformation> contractInfoSource;

    // Stored transformed code. Not necessary, but speeds up AVM contract calls.
    private ObjectStore<TransformedCodeInfo> transformedCodeSource;

    // TODO: include in the repository config after the FVM is decoupled or remove RepositoryConfig and pass individual parameters
    private int blockCacheSize;

    /**
     * used by getSnapShotTo
     *
     * <p>@ATTENTION: when do snap shot, another instance will be created. Make sure it is used only
     * by getSnapShotTo
     */
    protected AionRepositoryImpl() {}

    protected AionRepositoryImpl(RepositoryConfig repoConfig, int blockCacheSize) {
        this.blockCacheSize = blockCacheSize;
        this.cfg = repoConfig;
        init();
    }

    public static AionRepositoryImpl inst() {
        return AionRepositoryImplHolder.inst;
    }

    public static AionRepositoryImpl createForTesting(RepositoryConfig repoConfig) {
        return new AionRepositoryImpl(repoConfig, 0);
    }

    private void init() {
        try {
            initializeDatabasesAndCaches();

            // Setup the cache for transaction data source.
            this.transactionStore =
                    new TransactionStore(
                            transactionDatabase, AionTransactionStoreSerializer.serializer);

            // Setup block store.
            this.blockStore = new AionBlockStore(indexDatabase, blockDatabase, checkIntegrity, blockCacheSize);

            this.pendingStore = new PendingBlockStore(pendingStoreProperties);
            this.contractInfoSource = Stores.newObjectStoreWithCache(contractIndexDatabase, ContractInformation.RLP_SERIALIZER, 10, true);
            this.transformedCodeSource = Stores.newObjectStore(contractPerformCodeDatabase, TransformedCodeSerializer.RLP_SERIALIZER);

            // Setup world trie.
            worldState = createStateTrie();
        } catch (Exception e) {
            LOGGEN.error("Shutdown due to failure to initialize repository.");
            // the above message does not get logged without the printStackTrace below
            e.printStackTrace();
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }
    }

    public PendingBlockStore getPendingBlockStore() {
        return this.pendingStore;
    }

    /** @implNote The transaction store is not locked within the repository implementation. */
    public TransactionStore getTransactionStore() {
        return this.transactionStore;
    }

    private Trie createStateTrie() {
        return new SecureTrie(stateDSPrune).withPruningEnabled(pruneEnabled);
    }

    @Override
    public void updateBatch(
            Map<AionAddress, AccountState> stateCache,
            Map<AionAddress, ContractDetails> detailsCache,
            Map<AionAddress, TransformedCodeInfo> transformedCodeCache) {
        rwLock.writeLock().lock();

        try {
            for (Map.Entry<AionAddress, AccountState> entry : stateCache.entrySet()) {
                AionAddress address = entry.getKey();
                AccountState accountState = entry.getValue();
                ContractDetails contractDetails = detailsCache.get(address);

                if (accountState.isDeleted()) {
                    // TODO-A: batch operations here
                    try {
                        worldState.delete(address.toByteArray());
                    } catch (Exception e) {
                        LOG.error("key deleted exception [{}]", e.toString());
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("key deleted <key={}>", Hex.toHexString(address.toByteArray()));
                    }
                } else {

                    if (!contractDetails.isDirty()
                            || (contractDetails.getVmType() == InternalVmType.EITHER
                                    && !ContractInfo.isPrecompiledContract(address))) {
                        // code added because contract details are not reliably
                        // marked as dirty at present
                        // TODO: issue above will be solved with the conversion to a
                        // ContractState class
                        if (accountState.isDirty()) {
                            updateAccountState(address, accountState);

                            if (LOG.isTraceEnabled()) {
                                LOG.trace(
                                        "update: [{}],nonce: [{}] balance: [{}] [{}]",
                                        Hex.toHexString(address.toByteArray()),
                                        accountState.getNonce(),
                                        accountState.getBalance(),
                                        Hex.toHexString(contractDetails.getStorageHash()));
                            }
                        }
                        continue;
                    }

                    InnerContractDetails contractDetailsCache =
                            (InnerContractDetails) contractDetails;
                    if (contractDetailsCache.origContract == null) {
                        contractDetailsCache.origContract = detailsDS.newContractDetails(address, contractDetailsCache.getVmType());
                        contractDetailsCache.commit();
                    }

                    StoredContractDetails parentDetails = (StoredContractDetails) contractDetailsCache.origContract;

                    // this method requires the encoding functionality therefore can be applied only to StoredContractDetails
                    detailsDS.update(address, parentDetails);

                    accountState.setStateRoot(parentDetails.getStorageHash());

                    updateAccountState(address, accountState);

                    cachedContractIndex.put(address, Pair.of(ByteArrayWrapper.wrap(accountState.getCodeHash()), parentDetails.getVmType()));

                    if (LOG.isTraceEnabled()) {
                        LOG.trace(
                                "update: [{}],nonce: [{}] balance: [{}] [{}]",
                                Hex.toHexString(address.toByteArray()),
                                accountState.getNonce(),
                                accountState.getBalance(),
                                Hex.toHexString(parentDetails.getStorageHash()));
                    }
                }
            }

            for (Map.Entry<AionAddress, TransformedCodeInfo> entry : transformedCodeCache.entrySet()) {
                for (Map.Entry<ByteArrayWrapper, Map<Integer, byte[]>> infoMap : entry.getValue().transformedCodeMap.entrySet()) {
                    for (Map.Entry<Integer, byte[]> innerEntry : infoMap.getValue().entrySet()) {
                        setTransformedCode(entry.getKey(), infoMap.getKey().toBytes(), innerEntry.getKey(), innerEntry.getValue());
                    }
                }
            }

            LOG.trace("updated: detailsCache.size: {}", detailsCache.size());

            stateCache.clear();
            detailsCache.clear();
            transformedCodeCache.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
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
    public BigInteger getBalance(AionAddress address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getBalance();
    }

    @Override
    public ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key) {
        ContractDetails details = getContractDetails(address);
        return (details == null) ? null : details.get(key);
    }

    public final List<byte[]> getPoolTx() {

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

    public final List<byte[]> getCacheTx() {

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
            AionAddress address, Collection<ByteArrayWrapper> keys) {
        ContractDetails details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public byte[] getCode(AionAddress address) {
        AccountState accountState = getAccountState(address);

        if (accountState == null) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = accountState.getCodeHash();

        ContractDetails details = getContractDetails(address);
        return (details == null) ? EMPTY_BYTE_ARRAY : details.getCode(codeHash);
    }

    @Override
    public byte[] getTransformedCode(AionAddress address, byte[] codeHash, int avmVersion) {
        rwLock.readLock().lock();

        try {
            TransformedCodeInfo transformedCodeInfo = transformedCodeSource
                .get(address.toByteArray());

            if (transformedCodeInfo == null) {
                return null;
            } else {
                return transformedCodeInfo.getTransformedCode(ByteArrayWrapper.wrap(codeHash), avmVersion);
            }
        }
        finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void setTransformedCode(AionAddress address, byte[] codeHash, int avmVersion, byte[] transformedCode) {
        rwLock.writeLock().lock();

        try {
            TransformedCodeInfo transformedCodeInfo = transformedCodeSource.get(address.toByteArray());

            if (transformedCodeInfo == null) {
                transformedCodeInfo = new TransformedCodeInfo();
            }

            transformedCodeInfo.add(ByteArrayWrapper.wrap(codeHash), avmVersion, transformedCode);
            transformedCodeSource.put(address.toByteArray(), transformedCodeInfo);
        }
        finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public InternalVmType getVmType(AionAddress contract) {
        ContractDetails details = getContractDetails(contract);
        return (details == null) ? InternalVmType.EITHER : details.getVmType();
    }

    @Override
    public byte[] getObjectGraph(AionAddress contract) {
        ContractDetails details = getContractDetails(contract);
        return (details == null) ? EMPTY_BYTE_ARRAY : details.getObjectGraph();
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        AccountState account = getAccountState(address);
        return (account == null) ? BigInteger.ZERO : account.getNonce();
    }

    /** @implNote The method calling this method must handle the locking. */
    private void updateAccountState(AionAddress address, AccountState accountState) {
        // locked by calling method
        worldState.update(address.toByteArray(), accountState.getEncoded());
    }

    /**
     * @inheritDoc
     * @implNote Methods calling this can rely on the fact that the contract details returned is a
     *     newly created snapshot object. Since this method it locked, the methods using the
     *     returned object <b>do not need to be locked or synchronized</b>, depending on the
     *     specific use case.
     */
    @Override
    public StoredContractDetails getContractDetails(AionAddress address) {
        rwLock.readLock().lock();

        try {
            // That part is important cause if we have
            // to sync details storage according the trie root
            // saved in the account
            AccountState accountState = getAccountState(address);
            byte[] storageRoot = ConstantUtil.EMPTY_TRIE_HASH;
            byte[] codeHash = EMPTY_DATA_HASH;
            if (accountState != null) {
                storageRoot = accountState.getStateRoot();
                codeHash = accountState.getCodeHash();
            }

            InternalVmType vm = getVMUsed(address, codeHash);
            return detailsDS.getSnapshot(vm, address.toByteArray(), storageRoot);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public boolean hasContractDetails(AionAddress address) {
        rwLock.readLock().lock();
        try {
            return detailsDS.isPresent(address.toByteArray());
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
    public AccountState getAccountState(AionAddress address) {
        rwLock.readLock().lock();

        AccountState result = null;

        try {
            byte[] accountData = worldState.get(address.toByteArray());

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
    public boolean hasAccountState(AionAddress address) {
        return getAccountState(address) != null;
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

    public int getPruneBlockCount() {
        return this.pruneBlockCount;
    }

    public void commitBlock(ByteArrayWrapper blockHash, long blockNumber, byte[] blockStateRoot) {
        rwLock.writeLock().lock();

        try {
            worldState.sync();

            if (pruneEnabled) {
                // cache the block number & hash for retrieval during pruneBlocks
                if (cacheForBlockPruning.containsKey(blockNumber)) {
                    cacheForBlockPruning.get(blockNumber).add(blockHash);
                } else {
                    Set<ByteArrayWrapper> hashes = new HashSet<>();
                    hashes.add(blockHash);
                    cacheForBlockPruning.put(blockNumber, hashes);
                }

                if (stateDSPrune.isArchiveEnabled() && blockNumber % archiveRate == 0) {
                    // archive block
                    worldState.saveDiffStateToDatabase(blockStateRoot, stateDSPrune.getArchiveSource());
                }
                stateDSPrune.storeBlockChanges(blockHash, blockNumber);
                detailsDS.getStorageDSPrune().storeBlockChanges(blockHash, blockNumber);
                pruneBlocks(blockNumber);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void pruneBlocks(long currentBlockNumber) {
        if (currentBlockNumber > bestBlockNumber) {
            // Prune only on increasing blocks
            long pruneBlockNumber = currentBlockNumber - pruneBlockCount;

            if (pruneBlockNumber >= 0) {
                // If the cacheForBlockPruning does not contain the block neither will the trie cache
                if (cacheForBlockPruning.containsKey(pruneBlockNumber)) {
                    // Prune all the blocks at that level
                    Set<ByteArrayWrapper> hashes = cacheForBlockPruning.remove(pruneBlockNumber);
                    for (ByteArrayWrapper hash : hashes) {
                        stateDSPrune.prune(hash, pruneBlockNumber);
                        detailsDS.getStorageDSPrune().prune(hash, pruneBlockNumber);
                    }
                } else {
                    // Unlikely case where the block was evicted from the cache due to too many side chains
                    // In this case we do not attempt to prune blocks on side chains.
                    byte[] pruneBlockHash = blockStore.getBlockHashByNumber(pruneBlockNumber);
                    if (pruneBlockHash != null) {
                        ByteArrayWrapper hash = ByteArrayWrapper.wrap(pruneBlockHash);
                        stateDSPrune.prune(hash, pruneBlockNumber);
                        detailsDS.getStorageDSPrune().prune(hash, pruneBlockNumber);
                    }
                }
            }
        }
        bestBlockNumber = currentBlockNumber;
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
            repo.transformedCodeSource = transformedCodeSource;
            repo.cfg = cfg;
            repo.stateDatabase = this.stateDatabase;
            repo.stateWithArchive = this.stateWithArchive;
            repo.stateDSPrune = this.stateDSPrune;
            repo.cacheForBlockPruning = this.cacheForBlockPruning;

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

    public void addPooledTxToDB(Map<byte[], byte[]> pooledTx) {
        if (pooledTx.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            txPoolDatabase.putBatch(pooledTx);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void addCachedTxToDB(Map<byte[], byte[]> cachedTx) {
        if (cachedTx.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            pendingTxCacheDatabase.putBatch(cachedTx);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void removePooledTxInDB(List<byte[]> pooledTx) {
        if (pooledTx.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            txPoolDatabase.deleteBatch(pooledTx);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void removeCachedTxInDB(List<byte[]> cachedTx) {
        if (cachedTx.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            pendingTxCacheDatabase.deleteBatch(cachedTx);
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
                if (contractInfoSource != null) {
                    contractInfoSource.close();
                    LOGGEN.info("contractInfoSource closed.");
                    contractInfoSource = null;
                }
            } catch (Exception e) {
                LOGGEN.error(
                        "Exception occurred while closing the pendingTxCacheDatabase store.", e);
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
                if (transactionStore != null) {
                    transactionStore.close();
                    LOGGEN.info("Transaction store closed.");
                    transactionStore = null;
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

            try {
                if (transformedCodeSource != null) {
                    transformedCodeSource.close();
                    LOGGEN.info("transformedCodeSource store closed.");
                    transformedCodeSource = null;
                }
            } catch (Exception e) {
                LOGGEN.error(
                        "Exception occurred while closing the contractTransformedCode store.", e);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves the underlying state database that sits below all caches.
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
     * Retrieves the underlying details database that sits below all caches.
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

    @VisibleForTesting
    public byte[] dumpImportableState(byte[] root, int limit, DatabaseType dbType) {
        Map<ByteArrayWrapper, byte[]> refs = getReferencedTrieNodes(root, limit, dbType);

        byte[][] elements = new byte[refs.size()][];
        int i = 0;
        for (ByteArrayWrapper ref : refs.keySet()) {
            elements[i] =
                    RLP.encodeList(
                            RLP.encodeElement(ref.toBytes()),
                            RLP.encodeElement(getTrieNode(ref.toBytes(), dbType)));
            i++;
        }
        return RLP.encodeList(elements);
    }

    @VisibleForTesting
    public void loadImportableState(byte[] fullState, DatabaseType dbType) {
        RLPList data = RLP.decode2(fullState);
        RLPList elements = (RLPList) data.get(0);
        for (RLPElement element : elements) {
            data = (RLPList) element;
            importTrieNode(data.get(0).getRLPData(), data.get(1).getRLPData(), dbType);
        }
    }

    @VisibleForTesting
    public List<byte[]> getReferencedStorageNodes(byte[] value, int limit, AionAddress contract) {
        if (limit <= 0) {
            return Collections.emptyList();
        } else {
            byte[] subKey = h256(("details-storage/" + contract.toString()).getBytes());

            ByteArrayKeyValueStore db =
                    new XorDataSource(selectDatabase(DatabaseType.STORAGE), subKey);

            Trie trie = new SecureTrie(db);
            Map<ByteArrayWrapper, byte[]> refs = trie.getReferencedTrieNodes(value, limit);
            List<byte[]> converted = new ArrayList<>();
            for (ByteArrayWrapper key : refs.keySet()) {
                converted.add(ByteUtil.xorAlignRight(key.toBytes(), subKey));
            }
            return converted;
        }
    }

    @VisibleForTesting
    public byte[] dumpImportableStorage(byte[] root, int limit, AionAddress contract) {
        List<byte[]> refs = getReferencedStorageNodes(root, limit, contract);

        byte[][] elements = new byte[refs.size()][];
        int i = 0;
        for (byte[] ref : refs) {
            elements[i] =
                    RLP.encodeList(
                            RLP.encodeElement(ref),
                            RLP.encodeElement(getTrieNode(ref, DatabaseType.STORAGE)));
            i++;
        }
        return RLP.encodeList(elements);
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
    public ContractInformation getIndexedContractInformation(AionAddress contract) {
        return contract == null ? null : contractInfoSource.get(contract.toByteArray());
    }

    /**
     * Stores information regarding the given contract address for indexing and fast retrieval.
     *
     * @param contract the contract address for which information is being indexed
     * @param codeHash the hash of the code used at contract deployment allowing distinction between
     *     contracts deployed on different chains
     * @param inceptionBlock the block where the contract was created (to be used for sync through
     *     state transfer)
     * @param vmUsed the virtual machine used at the contract deployment (used to delegate contract
     *     call transactions to the appropriate virtual machine)
     * @param complete flag indicating if the data regarding this contract is complete (used by
     *     future light clients and during sync through state transfer to ensure the contract data
     *     is fully downloaded)
     */
    @VisibleForTesting
    public void saveIndexedContractInformation(
            AionAddress contract,
            ByteArrayWrapper codeHash,
            ByteArrayWrapper inceptionBlock,
            InternalVmType vmUsed,
            boolean complete) {
        if (contract != null) {
            ContractInformation ci = getIndexedContractInformation(contract);
            if (ci == null) {
                contractInfoSource.put(
                        contract.toByteArray(),
                        new ContractInformation(codeHash, vmUsed, inceptionBlock, complete));
            } else {
                // update the existing entry to add new information
                ci.append(codeHash, vmUsed, inceptionBlock, complete);

                // overwrites entry with new value
                contractInfoSource.put(contract.toByteArray(), ci);
            }
        }
    }

    private Map<AionAddress, Pair<ByteArrayWrapper, InternalVmType>> cachedContractIndex =
            new HashMap<>();

    /** Indexes the contract information. */
    public void commitCachedVMs(ByteArrayWrapper inceptionBlock) {
        for (Map.Entry<AionAddress, Pair<ByteArrayWrapper, InternalVmType>> entry :
                cachedContractIndex.entrySet()) {

            AionAddress contract = entry.getKey();
            ByteArrayWrapper codeHash = entry.getValue().getLeft();
            InternalVmType vm = entry.getValue().getRight();

            // write only if not already stored
            ContractInformation ci = getIndexedContractInformation(contract);
            if (ci == null || !ci.getVmUsed(codeHash.toBytes()).isContract()) {
                saveIndexedContractInformation(contract, codeHash, inceptionBlock, vm, true);
            } else {
                if (ci != null && ci.getVmUsed(codeHash.toBytes()) != vm) {
                    // possibly same code hash for AVM and FVM
                    LOG.error(
                            "The stored VM type does not match the cached VM type for the contract {} with code hash {}.",
                            contract,
                            codeHash);
                }
            }
        }
        cachedContractIndex.clear();
    }

    /**
     * Called by the blockchain after the current block has been fully processed and before the
     * contract information is indexed.
     */
    public void clearCachedVMs() {
        cachedContractIndex.clear();
    }

    /** Used when the codeHash is already known. */
    public InternalVmType getVMUsed(AionAddress contract, byte[] codeHash) {
        if (ContractInfo.isPrecompiledContract(contract)) {
            // skip the call to disk
            return InternalVmType.FVM;
        } else {
            if (cachedContractIndex.containsKey(contract)) {
                // block has not been committed yet
                return cachedContractIndex.get(contract).getRight();
            } else {
                // when there is no code the transaction can be processed as a balance transfer by either VM
                // skipping the database lookup in this case
                if (Arrays.equals(codeHash, EMPTY_DATA_HASH)) {
                    return InternalVmType.EITHER;
                } else {
                    ContractInformation ci = getIndexedContractInformation(contract);
                    if (ci == null) {
                        // signals that the value is not set
                        return InternalVmType.UNKNOWN;
                    } else {
                        return ci.getVmUsed(codeHash);
                    }
                }
            }
        }
    }

    public void removePoolTx() {
        rwLock.readLock().lock();
        try {
            txPoolDatabase.drop();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void removeCacheTx() {
        rwLock.readLock().lock();
        try {
            pendingTxCacheDatabase.drop();
        } finally {
            rwLock.readLock().unlock();
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
                                config.getDb()),
                        10);
    }

    /**
     * Determines if the given block (referenced by hash and number) is already stored in the repository.
     *
     * @return {@code true} if the given block exists in the block store, {@code false} otherwise.
     * @implNote The number is used to optimize the search by comparing it to the largest known block height.
     */
    public boolean isBlockStored(byte[] hash, long number) {
        return blockStore.isBlockStored(hash, number);
    }

    /**
     * Removes blocks on side chains and recreates the block information inside the index database.
     */
    public void pruneAndCorrectBlockStore() {
        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            LOGGEN.error("Empty database. Nothing to do.");
            return;
        } else {
            // revert to block number and flush changes
            blockStore.pruneAndCorrect();
            blockStore.flush();
        }
    }
}
