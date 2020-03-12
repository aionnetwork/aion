package org.aion.zero.impl.db;

import static org.aion.zero.impl.db.DatabaseUtils.connectAndOpen;
import static org.aion.zero.impl.db.DatabaseUtils.verifyAndBuildPath;
import static org.aion.zero.impl.db.DatabaseUtils.verifyDBfileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.AccountState;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.store.ArchivedDataSource;
import org.aion.db.store.JournalPruneDataSource;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.exception.InvalidFileTypeException;
import org.aion.zero.impl.config.CfgDb.Names;
import org.aion.zero.impl.config.CfgDb.Props;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.aion.zero.impl.trie.Trie;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

// import org.aion.dbmgr.exception.DriverManagerNoSuitableDriverRegisteredException;

/** Abstract Repository class. */
public abstract class AbstractRepository implements Repository<AccountState> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    protected static final Logger LOGGEN = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    /** ********* Database Name Constants ********** */
    protected static final String TRANSACTION_DB = Names.TRANSACTION;

    protected static final String INDEX_DB = Names.INDEX;
    protected static final String BLOCK_DB = Names.BLOCK;
    protected static final String PENDING_BLOCK_DB = Names.PENDING_BLOCK;
    protected static final String CONTRACT_INDEX_DB = Names.CONTRACT_INDEX;
    protected static final String DETAILS_DB = Names.DETAILS;
    protected static final String STORAGE_DB = Names.STORAGE;
    protected static final String GRAPH_DB = Names.GRAPH;
    protected static final String STATE_DB = Names.STATE;
    protected static final String STATE_ARCHIVE_DB = Names.STATE_ARCHIVE;
    protected static final String PENDING_TX_POOL_DB = Names.TX_POOL;
    protected static final String PENDING_TX_CACHE_DB = Names.TX_CACHE;
    protected static final String CONTRACT_PERFORM_CODE_DB = Names.CONTRACT_PERFORM_CODE;

    // State trie.
    protected Trie worldState;

    // DB Path
    // protected final static String DB_PATH = new
    // File(System.getProperty("user.dir"), "database").getAbsolutePath();

    /** ******** Database and Cache parameters ************* */
    protected ByteArrayKeyValueDatabase transactionDatabase;

    protected ByteArrayKeyValueDatabase contractIndexDatabase;
    protected ByteArrayKeyValueDatabase detailsDatabase;
    protected ByteArrayKeyValueDatabase storageDatabase;
    protected ByteArrayKeyValueDatabase graphDatabase;
    protected ByteArrayKeyValueDatabase indexDatabase;
    protected ByteArrayKeyValueDatabase blockDatabase;
    protected ByteArrayKeyValueDatabase stateDatabase;
    protected ByteArrayKeyValueDatabase stateArchiveDatabase;
    protected ByteArrayKeyValueDatabase txPoolDatabase;
    protected ByteArrayKeyValueDatabase pendingTxCacheDatabase;
    protected ByteArrayKeyValueDatabase contractPerformCodeDatabase;

    protected Collection<ByteArrayKeyValueDatabase> databaseGroup;

    protected ArchivedDataSource stateWithArchive;
    protected JournalPruneDataSource stateDSPrune;
    protected DetailsDataStore detailsDS;
    protected Map<Long, Set<ByteArrayWrapper>> cacheForBlockPruning;

    // Read Write Lock
    protected ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Block related parameters.
    protected long bestBlockNumber = 0;
    protected int pruneBlockCount;
    protected long archiveRate;
    protected boolean pruneEnabled = true;

    // Current blockstore.
    public AionBlockStore blockStore;

    // pending block store
    protected Properties pendingStoreProperties;

    // Flag to see if the current instance is a snapshot.
    protected boolean isSnapshot = false;

    protected boolean checkIntegrity = true;

    /**
     * Initializes all necessary databases and caches.
     *
     * @throws InvalidFilePathException when called with a persistent database vendor for which the
     *     data store cannot be created or opened.
     * @implNote This function is not locked. Locking must be done from calling function.
     */
    protected void initializeDatabasesAndCaches(RepositoryConfig cfg)
        throws InvalidFilePathException, InvalidFileTypeException, IOException {
        /*
         * Given that this function is not in the critical path and only called
         * on startup, enforce conditions here for safety
         */
        Objects.requireNonNull(cfg);
        //        Objects.requireNonNull(this.cfg.getVendorList());
        //        Objects.requireNonNull(this.cfg.getActiveVendor());

        //        /**
        //         * TODO: this is hack There should be some information on the
        //         * persistence of the DB so that we do not have to manually check.
        //         * Currently this information exists within
        //         * {@link DBVendor#getPersistenceMethod()}, but is not utilized.
        //         */
        //        if (this.cfg.getActiveVendor().equals(DBVendor.MOCKDB.toValue())) {
        //            LOG.warn("WARNING: Active vendor is set to MockDB, data will not persist");
        //        } else {

        DBVendor vendor = DBVendor.fromString(cfg.getDatabaseConfig(Names.DEFAULT).getProperty(Props.DB_TYPE));
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
        //        }
        //
        //        if (!Arrays.asList(this.cfg.getVendorList()).contains(this.cfg.getActiveVendor()))
        // {
        //
        //            ArrayList<String> vendorListString = new ArrayList<>();
        //            for (String v : this.cfg.getVendorList()) {
        //                vendorListString.add("\"" + v + "\"");
        //            }
        //            throw new DriverManagerNoSuitableDriverRegisteredException(
        //                    "Please check the vendor name field in /config/config.xml.\n"
        //                            + "No suitable driver found with name \"" +
        // this.cfg.getActiveVendor()
        //                            + "\".\nPlease select a driver from the following vendor list:
        // " + vendorListString);
        //        }

        Properties sharedProps;

        // Setup data stores
        try {
            databaseGroup = new ArrayList<>();

            checkIntegrity =
                    Boolean.valueOf(
                            cfg.getDatabaseConfig(Names.DEFAULT)
                                    .getProperty(Props.CHECK_INTEGRITY));

            // getting state specific properties
            sharedProps = getDatabaseConfig(cfg, STATE_DB, dbPath);
            this.stateDatabase = connectAndOpen(sharedProps, LOG);
            if (stateDatabase == null || stateDatabase.isClosed()) {
                throw newException(STATE_DB, sharedProps);
            }
            databaseGroup.add(stateDatabase);

            // getting transaction specific properties
            sharedProps = getDatabaseConfig(cfg, TRANSACTION_DB, dbPath);
            this.transactionDatabase = connectAndOpen(sharedProps, LOG);
            if (transactionDatabase == null || transactionDatabase.isClosed()) {
                throw newException(TRANSACTION_DB, sharedProps);
            }
            databaseGroup.add(transactionDatabase);

            // getting contract index specific properties
            // this db will be used only for fast sync
            sharedProps = getDatabaseConfig(cfg, CONTRACT_INDEX_DB, dbPath);
            this.contractIndexDatabase = connectAndOpen(sharedProps, LOG);
            if (contractIndexDatabase == null || contractIndexDatabase.isClosed()) {
                throw newException(CONTRACT_INDEX_DB, sharedProps);
            }
            databaseGroup.add(contractIndexDatabase);

            // getting contract perform code specific properties
            sharedProps = getDatabaseConfig(cfg, CONTRACT_PERFORM_CODE_DB, dbPath);
            this.contractPerformCodeDatabase = connectAndOpen(sharedProps, LOG);
            if (contractPerformCodeDatabase == null || contractPerformCodeDatabase.isClosed()) {
                throw newException(CONTRACT_PERFORM_CODE_DB, sharedProps);
            }
            databaseGroup.add(contractPerformCodeDatabase);

            // getting details specific properties
            sharedProps = getDatabaseConfig(cfg, DETAILS_DB, dbPath);
            this.detailsDatabase = connectAndOpen(sharedProps, LOG);
            if (detailsDatabase == null || detailsDatabase.isClosed()) {
                throw newException(DETAILS_DB, sharedProps);
            }
            databaseGroup.add(detailsDatabase);

            // getting storage specific properties
            sharedProps = getDatabaseConfig(cfg, STORAGE_DB, dbPath);
            this.storageDatabase = connectAndOpen(sharedProps, LOG);
            if (storageDatabase == null || storageDatabase.isClosed()) {
                throw newException(STORAGE_DB, sharedProps);
            }
            databaseGroup.add(storageDatabase);

            // getting graph specific properties
            sharedProps = getDatabaseConfig(cfg, GRAPH_DB, dbPath);
            this.graphDatabase = connectAndOpen(sharedProps, LOG);
            if (graphDatabase == null || graphDatabase.isClosed()) {
                throw newException(GRAPH_DB, sharedProps);
            }
            databaseGroup.add(graphDatabase);

            // getting index specific properties
            sharedProps = getDatabaseConfig(cfg, INDEX_DB, dbPath);
            this.indexDatabase = connectAndOpen(sharedProps, LOG);
            if (indexDatabase == null || indexDatabase.isClosed()) {
                throw newException(INDEX_DB, sharedProps);
            }
            databaseGroup.add(indexDatabase);

            // getting block specific properties
            sharedProps = getDatabaseConfig(cfg, BLOCK_DB, dbPath);
            this.blockDatabase = connectAndOpen(sharedProps, LOG);
            if (blockDatabase == null || blockDatabase.isClosed()) {
                throw newException(BLOCK_DB, sharedProps);
            }
            databaseGroup.add(blockDatabase);

            // using block specific properties
            sharedProps.setProperty(Props.DB_NAME, PENDING_BLOCK_DB);
            this.pendingStoreProperties = sharedProps;

            // getting pending tx pool specific properties
            sharedProps = getDatabaseConfig(cfg, PENDING_TX_POOL_DB, dbPath);
            this.txPoolDatabase = connectAndOpen(sharedProps, LOG);
            if (txPoolDatabase == null || txPoolDatabase.isClosed()) {
                throw newException(PENDING_TX_POOL_DB, sharedProps);
            }
            databaseGroup.add(txPoolDatabase);

            // getting pending tx cache specific properties
            sharedProps = getDatabaseConfig(cfg, PENDING_TX_CACHE_DB, dbPath);
            this.pendingTxCacheDatabase = connectAndOpen(sharedProps, LOG);
            if (pendingTxCacheDatabase == null || pendingTxCacheDatabase.isClosed()) {
                throw newException(PENDING_TX_CACHE_DB, sharedProps);
            }
            databaseGroup.add(pendingTxCacheDatabase);

            // Setup the cache for transaction data source.
            this.detailsDS =
                    new DetailsDataStore(detailsDatabase, storageDatabase, graphDatabase, LOG);

            // pruning config
            pruneEnabled = cfg.getPruneConfig().isEnabled();
            pruneBlockCount = cfg.getPruneConfig().getCurrentCount();
            archiveRate = cfg.getPruneConfig().getArchiveRate();

            if (pruneEnabled && cfg.getPruneConfig().isArchived()) {
                // using state config for state_archive
                sharedProps = getDatabaseConfig(cfg, STATE_ARCHIVE_DB, cfg.getDbPath());
                this.stateArchiveDatabase = connectAndOpen(sharedProps, LOG);
                databaseGroup.add(stateArchiveDatabase);

                stateWithArchive = new ArchivedDataSource(stateDatabase, stateArchiveDatabase);
                stateDSPrune = new JournalPruneDataSource(stateWithArchive, LOG);
                // the size is defined assuming for two side chain blocks at each level
                // since the pruned blocks are removed according to their level
                // in practice the cache is likely to be one third the allocated size
                cacheForBlockPruning = new HashMap<>(3 * pruneBlockCount);

                LOGGEN.info(
                        "Pruning and archiving ENABLED. Top block count set to {} and archive rate set to {}.",
                        pruneBlockCount,
                        archiveRate);
            } else {
                stateArchiveDatabase = null;
                stateWithArchive = null;
                stateDSPrune = new JournalPruneDataSource(stateDatabase, LOG);

                if (pruneEnabled) {
                    LOGGEN.info("Pruning ENABLED. Top block count set to {}.", pruneBlockCount);
                    cacheForBlockPruning = new HashMap<>(3 * pruneBlockCount);
                }
            }

            stateDSPrune.setPruneEnabled(pruneEnabled);
        } catch (Exception e) { // Setting up databases and caches went wrong.
            throw e;
        }
    }

    protected Properties getDatabaseConfig(RepositoryConfig cfg, String dbName, String dbPath) {
        Properties prop = cfg.getDatabaseConfig(dbName);
        prop.setProperty(Props.ENABLE_LOCKING, "false");
        prop.setProperty(Props.DB_PATH, dbPath);
        prop.setProperty(Props.DB_NAME, dbName);
        return prop;
    }

    private InvalidFilePathException newException(String dbName, Properties props) {
        return new InvalidFilePathException(
                "The «"
                        + dbName
                        + "» database from the repository could not be initialized with the given parameters: "
                        + props);
    }

    public AionBlockStore getBlockStore() {
        return this.blockStore;
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.blockStore.getBlockHashByNumber(blockNumber);
    }

    @Override
    public boolean isClosed() {
        return stateDatabase == null;
    }

    @Override
    public boolean isSnapshot() {
        return isSnapshot;
    }
}
