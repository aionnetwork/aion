package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.util.conversions.Hex.toHexString;
import static org.aion.zero.impl.config.CfgDb.Names.BLOCK;
import static org.aion.zero.impl.config.CfgDb.Names.CONTRACT_INDEX;
import static org.aion.zero.impl.config.CfgDb.Names.CONTRACT_PERFORM_CODE;
import static org.aion.zero.impl.config.CfgDb.Names.DEFAULT;
import static org.aion.zero.impl.config.CfgDb.Names.DETAILS;
import static org.aion.zero.impl.config.CfgDb.Names.GRAPH;
import static org.aion.zero.impl.config.CfgDb.Names.INDEX;
import static org.aion.zero.impl.config.CfgDb.Names.PENDING_BLOCK;
import static org.aion.zero.impl.config.CfgDb.Names.STATE;
import static org.aion.zero.impl.config.CfgDb.Names.STATE_ARCHIVE;
import static org.aion.zero.impl.config.CfgDb.Names.STORAGE;
import static org.aion.zero.impl.config.CfgDb.Names.TRANSACTION;
import static org.aion.zero.impl.config.CfgDb.Names.TX_CACHE;
import static org.aion.zero.impl.config.CfgDb.Names.TX_POOL;
import static org.aion.zero.impl.db.DatabaseUtils.connectAndOpen;
import static org.aion.zero.impl.db.DatabaseUtils.verifyAndBuildPath;
import static org.aion.zero.impl.db.DatabaseUtils.verifyDBfileType;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.AionTransaction;
import org.aion.base.ConstantUtil;
import org.aion.base.AccountState;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.db.impl.DBVendor;
import org.aion.db.store.ArchivedDataSource;
import org.aion.db.store.JournalPruneDataSource;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Stores;
import org.aion.db.store.XorDataSource;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.TransformedCodeInfo;
import org.aion.mcf.db.exception.InvalidFileTypeException;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.DataWord;
import org.aion.zero.impl.config.CfgDb.Props;
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
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.types.AionTxInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/** Has direct database connection. */
public final class AionRepositoryImpl implements Repository<AccountState> {

    // Logger
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    private static final Logger LOGGEN = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    // Read Write Lock
    private ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Databases used by the repository.
    private Collection<ByteArrayKeyValueDatabase> databaseGroup;
    @VisibleForTesting ByteArrayKeyValueDatabase transactionDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase contractIndexDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase detailsDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase storageDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase graphDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase indexDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase blockDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase stateDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase stateArchiveDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase txPoolDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase pendingTxCacheDatabase;
    @VisibleForTesting ByteArrayKeyValueDatabase contractPerformCodeDatabase;

    // Current block store.
    private AionBlockStore blockStore;

    // State trie and pruning setup.
    private Trie worldState;
    private JournalPruneDataSource stateDSPrune;
    private ArchivedDataSource stateWithArchive;
    private long bestBlockNumber;
    private int pruneBlockCount;
    private long archiveRate;
    private boolean pruneEnabled;

    private DetailsDataStore detailsDS;
    private TransactionStore transactionStore;

    // pending block store
    private PendingBlockStore pendingStore;

    // inferred contract information not used for consensus
    private ObjectStore<ContractInformation> contractInfoSource;

    // Stored transformed code. Not necessary, but speeds up AVM contract calls.
    private ObjectStore<TransformedCodeInfo> transformedCodeSource;

    // TODO: include in the repository config after the FVM is decoupled or remove RepositoryConfig and pass individual parameters
    private int blockCacheSize;

    // Flag to see if the current instance is a snapshot.
    private boolean isSnapshot = false;

    /**
     * used by getSnapShotTo
     *
     * <p>@ATTENTION: when do snap shot, another instance will be created. Make sure it is used only
     * by getSnapShotTo
     */
    private AionRepositoryImpl() {}

    private AionRepositoryImpl(RepositoryConfig repoConfig, int blockCacheSize) {
        this.blockCacheSize = blockCacheSize;
        init(repoConfig);
    }

    public static AionRepositoryImpl inst() {
        return AionRepositoryImplHolder.inst;
    }

    public static AionRepositoryImpl createForTesting(RepositoryConfig repoConfig) {
        return new AionRepositoryImpl(repoConfig, 0);
    }

    private void init(RepositoryConfig cfg) {
        try {
            initializeDatabasesAndCaches(cfg);

            // Setup the cache for the contract details data source.
            detailsDS = new DetailsDataStore(detailsDatabase, storageDatabase, graphDatabase, LOG);

            // Setup the cache for transaction data source.
            this.transactionStore =
                    new TransactionStore(
                            transactionDatabase, AionTransactionStoreSerializer.serializer);

            // Setup block store. Read integrity check flag (set to perform a block store integrity check at startup) directly from config.
            blockStore = new AionBlockStore(indexDatabase, blockDatabase, Boolean.valueOf(cfg.getDatabaseConfig(DEFAULT).getProperty(Props.CHECK_INTEGRITY)), blockCacheSize);

            pendingStore = new PendingBlockStore(getDatabaseConfig(cfg, PENDING_BLOCK, cfg.getDbPath()));
            this.contractInfoSource = Stores.newObjectStoreWithCache(contractIndexDatabase, ContractInformation.RLP_SERIALIZER, 10, true);
            this.transformedCodeSource = Stores.newObjectStore(contractPerformCodeDatabase, TransformedCodeSerializer.RLP_SERIALIZER);

            // State and pruning config.
            if (cfg.getPruneConfig().isArchived()) {
                setupSpreadPruning(cfg.getPruneConfig().getCurrentCount(), cfg.getPruneConfig().getArchiveRate(), getDatabaseConfig(cfg, STATE_ARCHIVE, cfg.getDbPath()));
            } else if (cfg.getPruneConfig().isEnabled()) {
                setupTopPruning(cfg.getPruneConfig().getCurrentCount());
            } else {
                // disable state pruning
                pruneEnabled = false;
                stateArchiveDatabase = null;
                stateWithArchive = null;
                stateDSPrune = new JournalPruneDataSource(stateDatabase, LOG);
                stateDSPrune.setPruneEnabled(pruneEnabled);
                // Setup world trie.
                worldState = createStateTrie();
            }
        } catch (Exception e) {
            LOGGEN.error("Shutdown due to failure to initialize repository.");
            // the above message does not get logged without the printStackTrace below
            e.printStackTrace();
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }
    }

    @VisibleForTesting
    public void setupSpreadPruning(int blockCount, int rate, Properties dbConfig) {
        this.pruneEnabled = true;
        this.pruneBlockCount = blockCount;
        this.bestBlockNumber = 0L;
        this.archiveRate = rate;

        // using state config for state_archive
        stateArchiveDatabase = connectAndOpen(dbConfig, LOG);
        databaseGroup.add(stateArchiveDatabase);

        stateWithArchive = new ArchivedDataSource(stateDatabase, stateArchiveDatabase);
        stateDSPrune = new JournalPruneDataSource(stateWithArchive, LOG);

        stateDSPrune.setPruneEnabled(pruneEnabled);
        worldState = createStateTrie();

        LOGGEN.info("Pruning and archiving ENABLED. Top block count set to {} and archive rate set to {}.", pruneBlockCount, archiveRate);
    }

    @VisibleForTesting
    public void setupTopPruning(int blockCount) {
        this.pruneEnabled = true;
        this.pruneBlockCount = blockCount;
        this.bestBlockNumber = 0L;

        stateArchiveDatabase = null;
        stateWithArchive = null;
        stateDSPrune = new JournalPruneDataSource(stateDatabase, LOG);

        stateDSPrune.setPruneEnabled(pruneEnabled);
        worldState = createStateTrie();

        LOGGEN.info("Pruning ENABLED. Top block count set to {}.", pruneBlockCount);
    }

    /**
     * Initializes all necessary databases and caches.
     *
     * @throws IllegalStateException when called with a persistent database vendor for which the
     *     data store cannot be created or opened.
     * @implNote This function is not locked. Locking must be done from calling function.
     */
    private void initializeDatabasesAndCaches(RepositoryConfig cfg)
            throws InvalidFileTypeException, IOException {
        // Given that this function is only called on startup, enforce conditions here for safety.
        Objects.requireNonNull(cfg);

        DBVendor vendor = DBVendor.fromString(cfg.getDatabaseConfig(DEFAULT).getProperty(Props.DB_TYPE));
        LOGGEN.info("The DB vendor is: {}", vendor);

        String dbPath = cfg.getDbPath();
        boolean isPersistent = vendor.isFileBased();
        if (isPersistent) {
            // verify user-provided path
            File f = new File(dbPath);
            verifyAndBuildPath(f);

            if (vendor.equals(DBVendor.LEVELDB) || vendor.equals(DBVendor.ROCKSDB)) {
                verifyDBfileType(f, vendor.toValue());
            }
        }

        Properties sharedProps;
        databaseGroup = new ArrayList<>();

        // getting state specific properties
        sharedProps = getDatabaseConfig(cfg, STATE, dbPath);
        this.stateDatabase = connectAndOpen(sharedProps, LOG);
        if (stateDatabase == null || stateDatabase.isClosed()) {
            throw newException(STATE, sharedProps);
        }
        databaseGroup.add(stateDatabase);

        // getting transaction specific properties
        sharedProps = getDatabaseConfig(cfg, TRANSACTION, dbPath);
        this.transactionDatabase = connectAndOpen(sharedProps, LOG);
        if (transactionDatabase == null || transactionDatabase.isClosed()) {
            throw newException(TRANSACTION, sharedProps);
        }
        databaseGroup.add(transactionDatabase);

        // getting contract index specific properties
        // this db will be used only for fast sync
        sharedProps = getDatabaseConfig(cfg, CONTRACT_INDEX, dbPath);
        this.contractIndexDatabase = connectAndOpen(sharedProps, LOG);
        if (contractIndexDatabase == null || contractIndexDatabase.isClosed()) {
            throw newException(CONTRACT_INDEX, sharedProps);
        }
        databaseGroup.add(contractIndexDatabase);

        // getting contract perform code specific properties
        sharedProps = getDatabaseConfig(cfg, CONTRACT_PERFORM_CODE, dbPath);
        this.contractPerformCodeDatabase = connectAndOpen(sharedProps, LOG);
        if (contractPerformCodeDatabase == null || contractPerformCodeDatabase.isClosed()) {
            throw newException(CONTRACT_PERFORM_CODE, sharedProps);
        }
        databaseGroup.add(contractPerformCodeDatabase);

        // getting details specific properties
        sharedProps = getDatabaseConfig(cfg, DETAILS, dbPath);
        this.detailsDatabase = connectAndOpen(sharedProps, LOG);
        if (detailsDatabase == null || detailsDatabase.isClosed()) {
            throw newException(DETAILS, sharedProps);
        }
        databaseGroup.add(detailsDatabase);

        // getting storage specific properties
        sharedProps = getDatabaseConfig(cfg, STORAGE, dbPath);
        this.storageDatabase = connectAndOpen(sharedProps, LOG);
        if (storageDatabase == null || storageDatabase.isClosed()) {
            throw newException(STORAGE, sharedProps);
        }
        databaseGroup.add(storageDatabase);

        // getting graph specific properties
        sharedProps = getDatabaseConfig(cfg, GRAPH, dbPath);
        this.graphDatabase = connectAndOpen(sharedProps, LOG);
        if (graphDatabase == null || graphDatabase.isClosed()) {
            throw newException(GRAPH, sharedProps);
        }
        databaseGroup.add(graphDatabase);

        // getting index specific properties
        sharedProps = getDatabaseConfig(cfg, INDEX, dbPath);
        this.indexDatabase = connectAndOpen(sharedProps, LOG);
        if (indexDatabase == null || indexDatabase.isClosed()) {
            throw newException(INDEX, sharedProps);
        }
        databaseGroup.add(indexDatabase);

        // getting block specific properties
        sharedProps = getDatabaseConfig(cfg, BLOCK, dbPath);
        this.blockDatabase = connectAndOpen(sharedProps, LOG);
        if (blockDatabase == null || blockDatabase.isClosed()) {
            throw newException(BLOCK, sharedProps);
        }
        databaseGroup.add(blockDatabase);

        // getting pending tx pool specific properties
        sharedProps = getDatabaseConfig(cfg, TX_POOL, dbPath);
        this.txPoolDatabase = connectAndOpen(sharedProps, LOG);
        if (txPoolDatabase == null || txPoolDatabase.isClosed()) {
            throw newException(TX_POOL, sharedProps);
        }
        databaseGroup.add(txPoolDatabase);

        // getting pending tx cache specific properties
        sharedProps = getDatabaseConfig(cfg, TX_CACHE, dbPath);
        this.pendingTxCacheDatabase = connectAndOpen(sharedProps, LOG);
        if (pendingTxCacheDatabase == null || pendingTxCacheDatabase.isClosed()) {
            throw newException(TX_CACHE, sharedProps);
        }
        databaseGroup.add(pendingTxCacheDatabase);
    }

    private Properties getDatabaseConfig(RepositoryConfig cfg, String dbName, String dbPath) {
        Properties prop = cfg.getDatabaseConfig(dbName);
        prop.setProperty(Props.ENABLE_LOCKING, "false");
        prop.setProperty(Props.DB_PATH, dbPath);
        prop.setProperty(Props.DB_NAME, dbName);
        return prop;
    }

    private IllegalStateException newException(String dbName, Properties props) {
        // A shutdown is required if the databases cannot be initialized.
        return new IllegalStateException(
                "The «"
                        + dbName
                        + "» database from the repository could not be initialized with the given parameters: "
                        + props);
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

    /**
     * Returns {@code true} only if the specified account has non-empty storage associated with it. Otherwise {@code false}.
     *
     * @param address The account address.
     * @return whether the account has non-empty storage or not.
     */
    @Override
    public boolean hasStorage(AionAddress address) {
        return getAccountState(address).hasStorage();
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
                byte[] pruneBlockHash = blockStore.getBlockHashByNumber(pruneBlockNumber);
                if (pruneBlockHash != null) {
                    ByteArrayWrapper hash = ByteArrayWrapper.wrap(pruneBlockHash);
                    stateDSPrune.prune(hash, pruneBlockNumber);
                    detailsDS.getStorageDSPrune().prune(hash, pruneBlockNumber);
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
    public void pruneAndCorrectBlockStore(Logger log) {
        Block bestBlock = getBestBlock();
        if (bestBlock == null) {
            log.error("Empty database. Nothing to do.");
            return;
        } else {
            // revert to block number and flush changes
            blockStore.pruneAndCorrect(log);
            blockStore.flush();
        }
    }

    public BigInteger getTotalDifficultyForHash(byte[] blockHash) {
        return this.blockStore.getTotalDifficultyForHash(blockHash);
    }

    /**
     * Saves the genesis block data inside the repository.
     *
     * @param genesis the genesis block to be flushed into the repository
     */
    public void buildGenesis(AionGenesis genesis) {
        // initialization section for network balance contract
        RepositoryCache track = startTracking();

        AionAddress networkBalanceAddress = ContractInfo.TOTAL_CURRENCY.contractAddress;
        track.createAccount(networkBalanceAddress);
        // saving FVM type for networkBalance contract
        track.saveVmType(networkBalanceAddress, InternalVmType.FVM);

        for (Map.Entry<Integer, BigInteger> addr : genesis.getNetworkBalances().entrySet()) {
            // assumes only additions are performed in the genesis
            track.addStorageRow(networkBalanceAddress,
                    new DataWord(addr.getKey()).toWrapper(),
                    wrapValueForPut(new DataWord(addr.getValue())));
        }

        for (AionAddress addr : genesis.getPremine().keySet()) {
            track.createAccount(addr);
            track.addBalance(addr, genesis.getPremine().get(addr).getBalance());
        }
        track.flush();

        commitBlock(genesis.getHashWrapper(), genesis.getNumber(), genesis.getStateRoot());
        blockStore.saveBlock(genesis, genesis.getDifficultyBI(), true);
    }

    private static ByteArrayWrapper wrapValueForPut(DataWord value) {
        return (value.isZero())
                ? value.toWrapper()
                : ByteArrayWrapper.wrap(value.getNoLeadZeroesData());
    }

    /**
     * Method called by the blockchain when a block index is missing from the database.
     *
     * @param missingBlock the block that should have existed but is missing due to potential database corruption
     * @param bestBlock the current best known block
     * @param log the log used for printing messages
     * @return {@code true} is the recovery was successful, {@code false} otherwise
     */
    public boolean recoverIndexEntry(Block missingBlock, Block bestBlock, Logger log) {
        Deque<Block> dirtyBlocks = new ArrayDeque<>();
        // already known to be missing the state
        dirtyBlocks.push(missingBlock);

        Block other = missingBlock;

        // find all the blocks missing a world state
        do {
            other = blockStore.getBlockByHash(other.getParentHash());

            // cannot recover if no valid states exist (must build from genesis)
            if (other == null) {
                return false;
            } else {
                dirtyBlocks.push(other);
            }
        } while (!isIndexed(other.getHash(), other.getNumber()) && other.getNumber() > 0);

        if (other.getNumber() == 0 && !isIndexed(other.getHash(), other.getNumber())) {
            log.info("Rebuild index FAILED because a valid index could not be found.");
            return false;
        }

        // if the size key is missing we set it to the MAX(best block, this block, current value)
        long maxNumber = blockStore.getMaxNumber();
        if (bestBlock != null && bestBlock.getNumber() > maxNumber) {
            maxNumber = bestBlock.getNumber();
        }
        if (missingBlock.getNumber() > maxNumber) {
            maxNumber = missingBlock.getNumber();
        }
        blockStore.correctSize(maxNumber, log);

        // remove the last added block because it has a correct world state
        Block parentBlock = blockStore.getBlockByHashWithInfo(dirtyBlocks.pop().getHash());

        BigInteger totalDiff = parentBlock.getTotalDifficulty();

        log.info(
                "Valid index found at block hash: {}, number: {}.",
                other.getShortHash(),
                other.getNumber());

        // rebuild world state for dirty blocks
        while (!dirtyBlocks.isEmpty()) {
            other = dirtyBlocks.pop();
            log.info(
                    "Rebuilding index for block hash: {}, number: {}, txs: {}.",
                    other.getShortHash(),
                    other.getNumber(),
                    other.getTransactionsList().size());
            totalDiff = blockStore.correctIndexEntry(other, parentBlock.getTotalDifficulty());
            parentBlock = other;
        }

        // update the repository
        flush();

        // return a flag indicating if the recovery worked
        if (isIndexed(missingBlock.getHash(), missingBlock.getNumber())) {
            Block mainChain = getBestBlock();
            BigInteger mainChainTotalDiff = getTotalDifficultyForHash(mainChain.getHash());

            // check if the main chain needs to be updated
            if (mainChainTotalDiff.compareTo(totalDiff) < 0) {
                if (log.isInfoEnabled()) {
                    log.info(
                            "branching: from = {}/{}, to = {}/{}",
                            mainChain.getNumber(),
                            toHexString(mainChain.getHash()),
                            missingBlock.getNumber(),
                            toHexString(missingBlock.getHash()));
                }
                blockStore.reBranch(missingBlock);
                syncToRoot(missingBlock.getStateRoot());
                flush();
            } else {
                if (mainChain.getNumber() > missingBlock.getNumber()) {
                    // checking if the current recovered blocks are a subsection of the main chain
                    Block ancestor = blockStore.getChainBlockByNumber(missingBlock.getNumber() + 1);
                    if (ancestor != null
                            && Arrays.equals(ancestor.getParentHash(), missingBlock.getHash())) {
                        blockStore.correctMainChain(missingBlock, log);
                        flush();
                    }
                }
            }
            return true;
        } else {
            log.info("Rebuild index FAILED.");
            return false;
        }
    }

    public Block getBestBlock() {
        return this.blockStore.getBestBlock();
    }

    /**
     * Performed before --redo-import to clear side chain blocks and reset the index.
     *
     * @param block the block that will be re-imported and should not be removed from the database
     */
    public void redoIndexWithoutSideChains(Block block) {
        this.blockStore.redoIndexWithoutSideChains(block);
    }

    public AionBlockStore getBlockStore() {
        return this.blockStore;
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.blockStore.getBlockHashByNumber(blockNumber);
    }

    public List<Block> getAllChainBlockByNumber(long number, Logger log) {
        return this.blockStore.getAllChainBlockByNumber(number, log);
    }

    @Override
    public boolean isSnapshot() {
        return isSnapshot;
    }

    /**
     * Used by internal world state recovery method.
     *
     * @return {@code true} if successful and {@code false} in case of any failure
     */
    public boolean revertTo(long nbBlock, Logger log) {
        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            log.error("Empty database. Nothing to do.");
            return false;
        }

        long nbBestBlock = bestBlock.getNumber();

        log.info("Attempting to revert best block from " + nbBestBlock + " to " + nbBlock + " ...");

        // exit with warning if the given block is larger or negative
        if (nbBlock < 0) {
            log.error("Negative values <" + nbBlock + "> cannot be interpreted as block numbers. Nothing to do.");
            return false;
        }
        if (nbBestBlock == 0) {
            log.error("Only genesis block in database. Nothing to do.");
            return false;
        }
        if (nbBlock == nbBestBlock) {
            log.error("The block " + nbBlock + " is the current best block stored in the database. Nothing to do.");
            return false;
        }
        if (nbBlock > nbBestBlock) {
            log.error("The block #"
                    + nbBlock
                    + " is greater than the current best block #"
                    + nbBestBlock
                    + " stored in the database. "
                    + "Cannot move to that block without synchronizing with peers. Start Aion instance to sync.");
            return false;
        }

        // revert to block number and flush changes
        blockStore.revert(nbBlock, log);
        blockStore.flush();

        nbBestBlock = blockStore.getBestBlock().getNumber();

        // ok if we managed to get down to the expected block
        return nbBestBlock == nbBlock;
    }

    public String dumpPastBlocks(long numberOfBlocks, String targetDirectory) throws IOException {
        return blockStore.dumpPastBlocks(numberOfBlocks, targetDirectory);
    }

    public void dumpTestData(long blockNumber, String[] otherParameters, String basePath, Logger log) {
        try {
            String file = blockStore.dumpPastBlocksForConsensusTest(blockNumber, basePath);
            if (file == null) {
                log.error("Illegal arguments. Cannot print block information.");
            } else {
                log.info("Block information stored in " + file);
            }
        } catch (IOException e) {
            log.error("Exception encountered while writing data to file.", e);
        }

        int paramIndex = 1;
        // print state for parent block
        Block parent = blockStore.getChainBlockByNumber(blockNumber - 1);
        if (parent == null) {
            log.error("Illegal arguments. Parent block is null.");
        } else {
            if (otherParameters.length > paramIndex && otherParameters[paramIndex].equals("skip-state")) {
                log.info("Parent state information is not retrieved.");
                paramIndex++;
            } else {
                try {
                    syncToRoot(parent.getStateRoot());

                    File file = new File(basePath, System.currentTimeMillis() + "-state-for-parent-block-" + parent.getNumber() + ".out");
                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));

                    writer.append(Hex.toHexString(dumpImportableState(parent.getStateRoot(), Integer.MAX_VALUE, DatabaseType.STATE)));
                    writer.newLine();

                    writer.close();
                    log.info("Parent state information stored in " + file.getName());
                } catch (IOException e) {
                    log.error("Exception encountered while writing data to file.", e);
                }
            }

            // print details and storage for the given contracts
            if (otherParameters.length > paramIndex) {
                try {
                    syncToRoot(parent.getStateRoot());
                    File file = new File(basePath, System.currentTimeMillis() + "-state-contracts.out");

                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));

                    // iterate through contracts
                    for (int i = paramIndex; i < otherParameters.length; i++) {

                        writer.append("Contract: " + AddressUtils.wrapAddress(otherParameters[i]));
                        writer.newLine();

                        StoredContractDetails details = getContractDetails(AddressUtils.wrapAddress(otherParameters[i]));

                        if (details != null) {
                            writer.append("Details: " + Hex.toHexString(details.getEncoded()));
                            writer.newLine();

                            writer.append("Storage: " + Hex.toHexString(dumpImportableStorage(details.getStorageHash(), Integer.MAX_VALUE, AddressUtils.wrapAddress(otherParameters[i]))));
                            writer.newLine();
                        }
                        writer.newLine();
                    }

                    writer.close();
                    log.info("Contract details and storage information stored in " + file.getName());
                } catch (IOException e) {
                    log.error("Exception encountered while writing data to file.", e);
                }
            }
        }
    }

    public void printStateTrieSize(long blockNumber, Logger log) {
        long topBlock = blockStore.getBestBlock().getNumber();
        if (topBlock < 0) {
            log.error("The database is empty. Cannot print block information.");
            return;
        }

        long targetBlock = topBlock - blockNumber + 1;
        if (targetBlock < 0) {
            targetBlock = 0;
        }

        Block block;
        byte[] stateRoot;

        while (targetBlock <= topBlock) {
            block = blockStore.getChainBlockByNumber(targetBlock);
            if (block != null) {
                stateRoot = block.getStateRoot();
                try {
                    log.info(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count = "
                                    + getWorldState().getTrieSize(stateRoot));
                } catch (RuntimeException e) {
                    log.error(
                            "Block hash: "
                                    + block.getShortHash()
                                    + ", number: "
                                    + block.getNumber()
                                    + ", tx count: "
                                    + block.getTransactionsList().size()
                                    + ", state trie kv count threw exception: "
                                    + e.getMessage());
                }
            } else {
                long count = blockStore.getBlocksByNumber(targetBlock).size();
                log.error(
                        "Null block found at level "
                                + targetBlock
                                + ". There "
                                + (count == 1 ? "is 1 block" : "are " + count + " blocks")
                                + " at this level. No main chain block found.");
            }
            targetBlock++;
        }
    }

    public void printStateTrieDump(long blockNumber, Logger log) {
        Block block;

        if (blockNumber == -1L) {
            block = blockStore.getBestBlock();
            if (block == null) {
                log.error("The requested block does not exist in the database.");
                return;
            }
            blockNumber = block.getNumber();
        } else {
            block = blockStore.getChainBlockByNumber(blockNumber);
            if (block == null) {
                log.error("The requested block does not exist in the database.");
                return;
            }
        }

        byte[] stateRoot = block.getStateRoot();
        log.info(
                "\nBlock hash: "
                        + block.getShortHash()
                        + ", number: "
                        + blockNumber
                        + ", tx count: "
                        + block.getTransactionsList().size()
                        + "\n\n"
                        + getWorldState().getTrieDump(stateRoot));
    }

    /**
     * @return {@code true} when the operation was successfult and {@code false} otherwise
     */
    public boolean queryTransaction(byte[] txHash, Logger log) {
        try {
            Map<ByteArrayWrapper, AionTxInfo> txInfoList = transactionStore.getTxInfo(txHash);

            if (txInfoList == null || txInfoList.isEmpty()) {
                log.error("Can not find the transaction with given hash.");
                return false;
            }

            for (Map.Entry<ByteArrayWrapper, AionTxInfo> entry : txInfoList.entrySet()) {

                Block block = blockStore.getBlockByHash(entry.getKey().toBytes());
                if (block == null) {
                    log.error("Cannot find the block data for the block hash from the transaction info. The database might be corrupted. Please consider reimporting the database by running ./aion.sh -n <network> --redo-import");
                    return false;
                }

                AionTransaction tx = block.getTransactionsList().get(entry.getValue().getIndex());

                if (tx == null) {
                    log.error("Cannot find the transaction data for the given hash. The database might be corrupted. Please consider reimporting the database by running ./aion.sh -n <network> --redo-import");
                    return false;
                }

                log.info(tx.toString());
                log.info(entry.getValue().toString());
            }

            return true;
        } catch (Exception e) {
            log.error("Error encountered while attempting to retrieve the transaction data.", e);
            return false;
        }
    }
}
