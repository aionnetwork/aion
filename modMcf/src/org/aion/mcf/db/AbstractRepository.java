/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/

package org.aion.mcf.db;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.IBlockHeader;
import org.aion.base.type.ITransaction;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.types.AbstractBlock;
import org.aion.mcf.vm.types.DataWord;
import org.slf4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aion.db.impl.DatabaseFactory.Props;

//import org.aion.dbmgr.exception.DriverManagerNoSuitableDriverRegisteredException;
// import org.aion.mcf.trie.JournalPruneDataSource;


/**
 * Abstract Repository class.
 */
public abstract class AbstractRepository<BLK extends AbstractBlock<BH, ? extends ITransaction>, BH extends IBlockHeader, BSB extends IBlockStoreBase<?, ?>>
        implements IRepository<AccountState, DataWord, BSB> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    protected static final Logger LOGGEN = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    // Configuration parameter
    protected IRepositoryConfig cfg;

    /*********** Database Name Constants ***********/

    protected static final String TRANSACTION_DB = "transaction";
    protected static final String INDEX_DB = "index";
    protected static final String BLOCK_DB = "block";
    protected static final String DETAILS_DB = "details";
    protected static final String STORAGE_DB = "storage";
    protected static final String STATE_DB = "state";

    // State trie.
    protected Trie worldState;

    // DB Path
    // protected final static String DB_PATH = new
    // File(System.getProperty("user.dir"), "database").getAbsolutePath();

    /********** Database and Cache parameters **************/

    protected IByteArrayKeyValueDatabase transactionDatabase;
    protected IByteArrayKeyValueDatabase detailsDatabase;
    protected IByteArrayKeyValueDatabase storageDatabase;
    protected IByteArrayKeyValueDatabase indexDatabase;
    protected IByteArrayKeyValueDatabase blockDatabase;
    protected IByteArrayKeyValueDatabase stateDatabase;

    protected Collection<IByteArrayKeyValueDatabase> databaseGroup;

    // protected JournalPruneDataSource<BLK, BH> stateDSPrune;
    protected DetailsDataStore<BLK, BH> detailsDS;

    // Read Write Lock
    protected ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Block related parameters.
    protected long bestBlockNumber = 0;
    protected long pruneBlockCount;
    protected boolean pruneEnabled = true;

    // Current blockstore.
    public BSB blockStore;

    // Flag to see if the current instance is a snapshot.
    protected boolean isSnapshot = false;

    /**
     * Initializes all necessary databases and caches.
     *
     * @throws Exception
     * @implNote This function is not locked. Locking must be done from calling function.
     */
    protected void initializeDatabasesAndCaches() throws Exception {
        /*
         * Given that this function is not in the critical path and only called
         * on startup, enforce conditions here for safety
         */
        Objects.requireNonNull(this.cfg);
        Objects.requireNonNull(this.cfg.getVendorList());
        Objects.requireNonNull(this.cfg.getActiveVendor());

        /**
         * TODO: this is hack There should be some information on the
         * persistence of the DB so that we do not have to manually check.
         * Currently this information exists within
         * {@link DBVendor#getPersistence()}, but is not utilized.
         */
        if (this.cfg.getActiveVendor().equals(DBVendor.MOCKDB.toValue())) {
            LOG.warn("WARNING: Active vendor is set to MockDB, data will not persist");
        } else {
            // verify user-provided path
            File f = new File(this.cfg.getDbPath());
            try {
                // ask the OS if the path is valid
                f.getCanonicalPath();

                // try to create the directory
                if (!f.exists()) {
                    f.mkdirs();
                }
            } catch (Exception e) {
                throw new InvalidFilePathException("Resolved file path \"" + this.cfg.getDbPath()
                        + "\" not valid as reported by the OS or a read/write permissions error occurred. Please provide an alternative DB file path in /config/config.xml.");
            }
        }

        if (!Arrays.asList(this.cfg.getVendorList()).contains(this.cfg.getActiveVendor())) {

            ArrayList<String> vendorListString = new ArrayList<>();
            for (String v : this.cfg.getVendorList()) {
                vendorListString.add("\"" + v + "\"");
            }
//            throw new DriverManagerNoSuitableDriverRegisteredException(
//                    "Please check the vendor name field in /config/config.xml.\n"
//                            + "No suitable driver found with name \"" + this.cfg.getActiveVendor()
//                            + "\".\nPlease select a driver from the following vendor list: " + vendorListString);
        }

        // TODO: these parameters should be converted to enum
        // should correspond with those listed in {@code DatabaseFactory}
        Properties sharedProps = new Properties();
        sharedProps.setProperty(Props.DB_TYPE, this.cfg.getActiveVendor());
        sharedProps.setProperty(Props.DB_PATH, this.cfg.getDbPath());
        sharedProps.setProperty(Props.ENABLE_AUTO_COMMIT, String.valueOf(this.cfg.isAutoCommitEnabled()));
        sharedProps.setProperty(Props.ENABLE_DB_CACHE, String.valueOf(this.cfg.isDbCacheEnabled()));
        sharedProps.setProperty(Props.ENABLE_DB_COMPRESSION, String.valueOf(this.cfg.isDbCompressionEnabled()));
        sharedProps.setProperty(Props.ENABLE_HEAP_CACHE, String.valueOf(this.cfg.isHeapCacheEnabled()));
        sharedProps.setProperty(Props.MAX_HEAP_CACHE_SIZE, this.cfg.getMaxHeapCacheSize());
        sharedProps.setProperty(Props.ENABLE_HEAP_CACHE_STATS, String.valueOf(this.cfg.isHeapCacheStatsEnabled()));
        sharedProps.setProperty(Props.MAX_FD_ALLOC, String.valueOf(this.cfg.getMaxFdAllocSize()));
        sharedProps.setProperty(Props.BLOCK_SIZE, String.valueOf(this.cfg.getBlockSize()));
        sharedProps.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(this.cfg.getWriteBufferSize()));
        sharedProps.setProperty(Props.DB_CACHE_SIZE, String.valueOf(this.cfg.getCacheSize()));

        try {
            databaseGroup = new ArrayList<>();

            /**
             * Setup datastores
             */
            // locking enabled for state
            sharedProps.setProperty(Props.ENABLE_LOCKING, "true");

            sharedProps.setProperty(Props.DB_NAME, STATE_DB);
            this.stateDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(stateDatabase);

            // locking disabled for other databases
            sharedProps.setProperty(Props.ENABLE_LOCKING, "false");

            sharedProps.setProperty(Props.DB_NAME, TRANSACTION_DB);
            this.transactionDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(transactionDatabase);

            sharedProps.setProperty(Props.DB_NAME, DETAILS_DB);
            this.detailsDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(detailsDatabase);

            sharedProps.setProperty(Props.DB_NAME, STORAGE_DB);
            this.storageDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(storageDatabase);

            sharedProps.setProperty(Props.DB_NAME, INDEX_DB);
            this.indexDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(indexDatabase);

            sharedProps.setProperty(Props.DB_NAME, BLOCK_DB);
            this.blockDatabase = connectAndOpen(sharedProps);
            databaseGroup.add(blockDatabase);

            // Setup the cache for transaction data source.
            this.detailsDS = new DetailsDataStore<>(detailsDatabase, storageDatabase, this.cfg);
            // disabling use of JournalPruneDataSource until functionality properly tested
            // TODO-AR: enable pruning with the JournalPruneDataSource
            // stateDSPrune = new JournalPruneDataSource<>(stateDatabase);
            pruneBlockCount = pruneEnabled ? this.cfg.getPrune() : -1;
        } catch (Exception e) { // Setting up databases and caches went wrong.
            throw e;
        }
    }

    @Override
    public BSB getBlockStore() {
        return this.blockStore;
    }

    @Override
    public boolean isClosed() {
        return stateDatabase == null;
    }

    private IByteArrayKeyValueDatabase connectAndOpen(Properties info) {
        // get the database object
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(info);

        // open the database connection
        db.open();

        // check object status
        if (db == null) {
            LOG.error("Database <{}> connection could not be established for <{}>.", info.getProperty(Props.DB_TYPE),
                    info.getProperty(Props.DB_NAME));
        }

        // check persistence status
        if (!db.isCreatedOnDisk()) {
            LOG.error("Database <{}> cannot be saved to disk for <{}>.", info.getProperty(Props.DB_TYPE),
                    info.getProperty(Props.DB_NAME));
        }

        return db;
    }
}
