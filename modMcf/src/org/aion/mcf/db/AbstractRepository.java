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
package org.aion.mcf.db;

import static org.aion.db.impl.DatabaseFactory.Props;
import static org.aion.mcf.db.DatabaseUtils.connectAndOpen;
import static org.aion.mcf.db.DatabaseUtils.verifyAndBuildPath;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.IBlockHeader;
import org.aion.base.type.ITransaction;
import org.aion.base.vm.IDataWord;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgDb.Names;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.aion.mcf.ds.ArchivedDataSource;
import org.aion.mcf.trie.JournalPruneDataSource;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.types.AbstractBlock;
import org.slf4j.Logger;

// import org.aion.dbmgr.exception.DriverManagerNoSuitableDriverRegisteredException;

/** Abstract Repository class. */
public abstract class AbstractRepository<
                BLK extends AbstractBlock<BH, ? extends ITransaction>,
                BH extends IBlockHeader,
                BSB extends IBlockStoreBase<?, ?>>
        implements IRepository<AccountState, IDataWord, BSB> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    protected static final Logger LOGGEN = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    // Configuration parameter
    protected IRepositoryConfig cfg;

    /** ********* Database Name Constants ********** */
    protected static final String TRANSACTION_DB = Names.TRANSACTION;

    protected static final String INDEX_DB = Names.INDEX;
    protected static final String BLOCK_DB = Names.BLOCK;
    protected static final String PENDING_BLOCK_DB = Names.PENDING_BLOCK;
    protected static final String DETAILS_DB = Names.DETAILS;
    protected static final String STORAGE_DB = Names.STORAGE;
    protected static final String STATE_DB = Names.STATE;
    protected static final String STATE_ARCHIVE_DB = Names.STATE_ARCHIVE;
    protected static final String PENDING_TX_POOL_DB = Names.TX_POOL;
    protected static final String PENDING_TX_CACHE_DB = Names.TX_CACHE;

    // State trie.
    protected Trie worldState;

    // DB Path
    // protected final static String DB_PATH = new
    // File(System.getProperty("user.dir"), "database").getAbsolutePath();

    /** ******** Database and Cache parameters ************* */
    protected IByteArrayKeyValueDatabase transactionDatabase;

    protected IByteArrayKeyValueDatabase detailsDatabase;
    protected IByteArrayKeyValueDatabase storageDatabase;
    protected IByteArrayKeyValueDatabase indexDatabase;
    protected IByteArrayKeyValueDatabase blockDatabase;
    protected IByteArrayKeyValueDatabase stateDatabase;
    protected IByteArrayKeyValueDatabase stateArchiveDatabase;
    protected IByteArrayKeyValueDatabase txPoolDatabase;
    protected IByteArrayKeyValueDatabase pendingTxCacheDatabase;

    protected Collection<IByteArrayKeyValueDatabase> databaseGroup;

    protected ArchivedDataSource stateWithArchive;
    protected JournalPruneDataSource stateDSPrune;
    protected DetailsDataStore<BLK, BH> detailsDS;

    // Read Write Lock
    protected ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Block related parameters.
    protected long bestBlockNumber = 0;
    protected long pruneBlockCount;
    protected long archiveRate;
    protected boolean pruneEnabled = true;

    // Current blockstore.
    public BSB blockStore;

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
    protected void initializeDatabasesAndCaches() throws InvalidFilePathException, URISyntaxException {
        /*
         * Given that this function is not in the critical path and only called
         * on startup, enforce conditions here for safety
         */
        Objects.requireNonNull(this.cfg);
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

        // if persistence is required
        URI dbPathUri = new URI(this.cfg.getDbPath());

        if (dbPathUri.getScheme() == null || dbPathUri.getScheme().equalsIgnoreCase("file")) {
            if (Boolean.valueOf(cfg.getDatabaseConfig(Names.DEFAULT).getProperty(Props.PERSISTENT))) {
                // verify user-provided path
                File f = new File(this.cfg.getDbPath());
                verifyAndBuildPath(f);
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
            sharedProps = cfg.getDatabaseConfig(STATE_DB);
            // locking enabled for state when JournalPrune not used
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, STATE_DB);
            this.stateDatabase = connectAndOpen(sharedProps, LOG);
            if (stateDatabase == null || stateDatabase.isClosed()) {
                throw newException(STATE_DB, sharedProps);
            }
            databaseGroup.add(stateDatabase);

            // getting transaction specific properties
            sharedProps = cfg.getDatabaseConfig(TRANSACTION_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, TRANSACTION_DB);
            this.transactionDatabase = connectAndOpen(sharedProps, LOG);
            if (transactionDatabase == null || transactionDatabase.isClosed()) {
                throw newException(TRANSACTION_DB, sharedProps);
            }
            databaseGroup.add(transactionDatabase);

            // getting details specific properties
            sharedProps = cfg.getDatabaseConfig(DETAILS_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, DETAILS_DB);
            this.detailsDatabase = connectAndOpen(sharedProps, LOG);
            if (detailsDatabase == null || detailsDatabase.isClosed()) {
                throw newException(DETAILS_DB, sharedProps);
            }
            databaseGroup.add(detailsDatabase);

            // getting storage specific properties
            sharedProps = cfg.getDatabaseConfig(STORAGE_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, STORAGE_DB);
            this.storageDatabase = connectAndOpen(sharedProps, LOG);
            if (storageDatabase == null || storageDatabase.isClosed()) {
                throw newException(STORAGE_DB, sharedProps);
            }
            databaseGroup.add(storageDatabase);

            // getting index specific properties
            sharedProps = cfg.getDatabaseConfig(INDEX_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, INDEX_DB);
            this.indexDatabase = connectAndOpen(sharedProps, LOG);
            if (indexDatabase == null || indexDatabase.isClosed()) {
                throw newException(INDEX_DB, sharedProps);
            }
            databaseGroup.add(indexDatabase);

            // getting block specific properties
            sharedProps = cfg.getDatabaseConfig(BLOCK_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, BLOCK_DB);
            this.blockDatabase = connectAndOpen(sharedProps, LOG);
            if (blockDatabase == null || blockDatabase.isClosed()) {
                throw newException(BLOCK_DB, sharedProps);
            }
            databaseGroup.add(blockDatabase);

            // using block specific properties
            sharedProps.setProperty(Props.DB_NAME, PENDING_BLOCK_DB);
            this.pendingStoreProperties = sharedProps;

            // getting pending tx pool specific properties
            sharedProps = cfg.getDatabaseConfig(PENDING_TX_POOL_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, PENDING_TX_POOL_DB);
            this.txPoolDatabase = connectAndOpen(sharedProps, LOG);
            if (txPoolDatabase == null || txPoolDatabase.isClosed()) {
                throw newException(PENDING_TX_POOL_DB, sharedProps);
            }
            databaseGroup.add(txPoolDatabase);

            // getting pending tx cache specific properties
            sharedProps = cfg.getDatabaseConfig(PENDING_TX_CACHE_DB);
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
            sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
            sharedProps.setProperty(Props.DB_NAME, PENDING_TX_CACHE_DB);
            this.pendingTxCacheDatabase = connectAndOpen(sharedProps, LOG);
            if (pendingTxCacheDatabase == null || pendingTxCacheDatabase.isClosed()) {
                throw newException(PENDING_TX_CACHE_DB, sharedProps);
            }
            databaseGroup.add(pendingTxCacheDatabase);

            // Setup the cache for transaction data source.
            this.detailsDS = new DetailsDataStore<>(detailsDatabase, storageDatabase, this.cfg);

            // pruning config
            pruneEnabled = this.cfg.getPruneConfig().isEnabled();
            pruneBlockCount = this.cfg.getPruneConfig().getCurrentCount();
            archiveRate = this.cfg.getPruneConfig().getArchiveRate();

            if (pruneEnabled && this.cfg.getPruneConfig().isArchived()) {
                // using state config for state_archive
                sharedProps = cfg.getDatabaseConfig(STATE_DB);
                sharedProps.setProperty(Props.ENABLE_LOCKING, "false");
                sharedProps.setProperty(Props.DB_PATH, cfg.getDbPath());
                sharedProps.setProperty(Props.DB_NAME, STATE_ARCHIVE_DB);
                this.stateArchiveDatabase = connectAndOpen(sharedProps, LOG);
                databaseGroup.add(stateArchiveDatabase);

                stateWithArchive = new ArchivedDataSource(stateDatabase, stateArchiveDatabase);
                stateDSPrune = new JournalPruneDataSource(stateWithArchive);

                LOGGEN.info(
                        "Pruning and archiving ENABLED. Top block count set to {} and archive rate set to {}.",
                        pruneBlockCount,
                        archiveRate);
            } else {
                stateArchiveDatabase = null;
                stateWithArchive = null;
                stateDSPrune = new JournalPruneDataSource(stateDatabase);

                if (pruneEnabled) {
                    LOGGEN.info("Pruning ENABLED. Top block count set to {}.", pruneBlockCount);
                }
            }

            stateDSPrune.setPruneEnabled(pruneEnabled);
        } catch (Exception e) { // Setting up databases and caches went wrong.
            throw e;
        }
    }

    private InvalidFilePathException newException(String dbName, Properties props) {
        return new InvalidFilePathException(
                "The «"
                        + dbName
                        + "» database from the repository could not be initialized with the given parameters: "
                        + props);
    }

    @Override
    public BSB getBlockStore() {
        return this.blockStore;
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
