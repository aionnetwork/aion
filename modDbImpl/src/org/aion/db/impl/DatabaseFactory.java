package org.aion.db.impl;

import java.util.Properties;
import org.aion.db.generic.LockedDatabase;
import org.aion.db.generic.SpecialLockedDatabase;
import org.aion.db.generic.TimedDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.PersistentMockDB;
import org.aion.db.impl.mongodb.MongoDB;
import org.aion.db.impl.rocksdb.RocksDBConstants;
import org.aion.db.impl.rocksdb.RocksDBWrapper;
import org.slf4j.Logger;

/**
 * Returns an instance of {@link ByteArrayKeyValueDatabase} based on the given properties.
 *
 * @author Alexandra Roatis
 */
public abstract class DatabaseFactory {

    // TODO AKI-349: refactor to avoid reliance on string properties
    public static class Props {
        public static final String DB_TYPE = "db_type";

        public static final String DB_NAME = "db_name";
        public static final String DB_PATH = "db_path";

        public static final String ENABLE_AUTO_COMMIT = "enable_auto_commit";
        public static final String ENABLE_DB_CACHE = "enable_db_cache";
        public static final String ENABLE_DB_COMPRESSION = "enable_db_compression";

        public static final String ENABLE_HEAP_CACHE_STATS = "enable_heap_cache_stats";
        public static final String MAX_HEAP_CACHE_SIZE = "max_heap_cache_size";

        public static final String ENABLE_LOCKING = "enable_locking";

    }

    public static ByteArrayKeyValueDatabase connect(Properties info, Logger log) {
        return connect(info, log, false);
    }

    public static ByteArrayKeyValueDatabase connect(Properties info, Logger log, boolean debug) {

        if (log == null) {
            throw new NullPointerException("Please provide a Logger for recording messages.");
        }

        DBVendor dbType = DBVendor.fromString(info.getProperty(Props.DB_TYPE));
        ByteArrayKeyValueDatabase db;

        if (dbType == DBVendor.UNKNOWN) {
            // the driver, if correct should check path and name
            db = connect(info.getProperty(Props.DB_TYPE), info, log);
        } else {

            boolean enableLocking = getBoolean(info, Props.ENABLE_LOCKING);

            // first check for locking
            if (enableLocking) {
                db = connectWithLocks(info, log);
            } else {
                db = connectBasic(info, log);
            }
        }

        // time operations during debug
        if (debug) {
            return new TimedDatabase(db, log);
        } else {
            return db;
        }
    }

    /**
     * If enabled, the topmost database will be the one enforcing the locking functionality.
     *
     * @return A database implementation with read-write locks.
     */
    private static ByteArrayKeyValueDatabase connectWithLocks(Properties info, Logger log) {
        DBVendor vendor = DBVendor.fromString(info.getProperty(Props.DB_TYPE));
        if (vendor == DBVendor.LEVELDB || vendor == DBVendor.ROCKSDB) {
            return new SpecialLockedDatabase(connectBasic(info, log), log);
        } else {
            return new LockedDatabase(connectBasic(info, log), log);
        }
    }

    /** @return A database implementation for each of the vendors in {@link DBVendor}. */
    private static AbstractDB connectBasic(Properties info, Logger log) {
        DBVendor dbType = DBVendor.fromString(info.getProperty(Props.DB_TYPE));

        String dbName = info.getProperty(Props.DB_NAME);

        if (dbType == DBVendor.MOCKDB) {
            // MockDB does not require name and path checks
            log.warn("WARNING: Active vendor for <{}> is set to MockDB, data will not persist!", dbName);
            return new MockDB(dbName, log);
        }

        String dbPath = info.getProperty(Props.DB_PATH);

        if (dbType == DBVendor.PERSISTENTMOCKDB) {
            log.warn("WARNING: Active vendor for <{}> is set to PersistentMockDB, data will be saved only at close!", dbName);
            return new PersistentMockDB(dbName, dbPath, log);
        }

        boolean enableDbCache = getBoolean(info, Props.ENABLE_DB_CACHE);
        boolean enableDbCompression = getBoolean(info, Props.ENABLE_DB_COMPRESSION);

        // ensure not null name for other databases
        if (dbName == null) {
            log.error("Please provide a database name value that is not null.");
            return null;
        }

        // ensure not null path for other databases
        if (dbPath == null) {
            log.error("Please provide a database path value that is not null.");
            return null;
        }

        // select database implementation
        switch (dbType) {
            case LEVELDB:
                {
                    return new LevelDB(
                            dbName,
                            dbPath,
                            log,
                            enableDbCache,
                            enableDbCompression,
                            LevelDBConstants.MAX_OPEN_FILES,
                            LevelDBConstants.BLOCK_SIZE,
                            LevelDBConstants.WRITE_BUFFER_SIZE,
                            LevelDBConstants.CACHE_SIZE);
                }
            case ROCKSDB:
                {
                    return new RocksDBWrapper(
                            dbName,
                            dbPath,
                            log,
                            enableDbCache,
                            enableDbCompression,
                            RocksDBConstants.MAX_OPEN_FILES,
                            RocksDBConstants.BLOCK_SIZE,
                            RocksDBConstants.WRITE_BUFFER_SIZE,
                            RocksDBConstants.READ_BUFFER_SIZE,
                            RocksDBConstants.CACHE_SIZE);
                }
            case H2:
                {
                    return new H2MVMap(dbName, dbPath, log, enableDbCache, enableDbCompression);
                }
            case MONGODB:
                {
                    return new MongoDB(dbName, dbPath, log);
                }
            default:
                break;
        }

        log.error("Invalid database type provided: {}", dbType);
        return null;
    }

    /**
     * @return A database implementation based on a driver implementing the {@link IDriver}
     *     interface.
     */
    public static ByteArrayKeyValueDatabase connect(String driverName, Properties info, Logger log) {
        try {
            // see if the given name is a valid driver
            IDriver driver =
                    ((Class<? extends IDriver>) Class.forName(driverName))
                            .getDeclaredConstructor()
                            .newInstance();
            // return a connection
            return driver.connect(info, log);
        } catch (Exception e) {
            log.error("Could not load database driver.", e);
        }

        log.error("Invalid database driver provided: {}", driverName);
        return null;
    }

    /** @return A mock database. */
    public static ByteArrayKeyValueDatabase connect(String dbName, Logger log) {
        return new MockDB(dbName, log);
    }

    private static boolean getBoolean(Properties info, String prop) {
        return Boolean.parseBoolean(info.getProperty(prop));
    }

    private static int getInt(Properties info, String prop, int defaultValue) {
        return Integer.parseInt(info.getProperty(prop, String.valueOf(defaultValue)));
    }
}
