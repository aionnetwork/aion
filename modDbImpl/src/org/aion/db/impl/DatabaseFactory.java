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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.db.impl;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.generic.DatabaseWithCache;
import org.aion.db.generic.LockedDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.Properties;

/**
 * Returns an instance of {@link IByteArrayKeyValueDatabase} based on the given properties.
 *
 * @author Alexandra Roatis
 */
public abstract class DatabaseFactory {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    public static final String PROP_DB_TYPE = "db_type";

    public static final String PROP_DB_NAME = "db_name";
    public static final String PROP_DB_PATH = "db_path";

    public static final String PROP_ENABLE_AUTO_COMMIT = "enable_auto_commit";
    public static final String PROP_ENABLE_DB_CACHE = "enable_db_cache";
    public static final String PROP_ENABLE_DB_COMPRESSION = "enable_db_compression";
    public static final String PROP_ENABLE_HEAP_CACHE = "enable_heap_cache";

    public static final String PROP_ENABLE_HEAP_CACHE_STATS = "enable_heap_cache_stats";
    public static final String PROP_MAX_HEAP_CACHE_SIZE = "max_heap_cache_size";

    public static final String PROP_ENABLE_LOCKING = "enable_locking";

    public static final String PROP_MAX_FD_ALLOC = "max_fd_alloc_size";
    public static final String PROP_BLOCK_SIZE = "block_size";

    public static final String PROP_WRITE_BUFFER_SIZE = "write_buffer_size";
    public static final String PROP_CACHE_SIZE = "cache_size";

    public static IByteArrayKeyValueDatabase connect(Properties info) {

        DBVendor dbType = DBVendor.fromString(info.getProperty(PROP_DB_TYPE));

        if (dbType == DBVendor.UNKNOWN) {
            // the driver, if correct should check path and name
            return connect(info.getProperty(PROP_DB_TYPE), info);
        }

        boolean enableLocking = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_LOCKING));

        // first check for locking
        if (enableLocking) {
            return connectWithLocks(info);
        }

        // next check for heap cache
        if (Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE))) {
            return connectWithCache(info);
        } else {
            return connectBasic(info);
        }
    }

    /**
     * If enabled, the topmost database will be the one enforcing the locking functionality.
     *
     * @return A database implementation with read-write locks.
     */
    private static IByteArrayKeyValueDatabase connectWithLocks(Properties info) {
        boolean enableHeapCache = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE));
        if (enableHeapCache) {
            return new LockedDatabase(connectWithCache(info));
        } else {
            return new LockedDatabase(connectBasic(info));
        }
    }

    /**
     * @return A database implementation with a caching layer.
     */
    private static IByteArrayKeyValueDatabase connectWithCache(Properties info) {
        boolean enableAutoCommit = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_AUTO_COMMIT));
        return new DatabaseWithCache(connectBasic(info), enableAutoCommit, info.getProperty(PROP_MAX_HEAP_CACHE_SIZE),
                Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE_STATS)));
    }

    /**
     * @return A database implementation for each of the vendors in {@link DBVendor}.
     */
    private static AbstractDB connectBasic(Properties info) {
        DBVendor dbType = DBVendor.fromString(info.getProperty(PROP_DB_TYPE));

        String dbName = info.getProperty(PROP_DB_NAME);

        if (dbType == DBVendor.MOCKDB) {
            // MockDB does not require name and path checks
            return new MockDB(dbName);
        }

        String dbPath = info.getProperty(PROP_DB_PATH);

        boolean enableDbCache = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_DB_CACHE));
        boolean enableDbCompression = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_DB_COMPRESSION));

        // ensure not null name for other databases
        if (dbName == null) {
            LOG.error("Please provide a database name value that is not null.");
            return null;
        }

        // ensure not null path for other databases
        if (dbPath == null) {
            LOG.error("Please provide a database path value that is not null.");
            return null;
        }

        // select database implementation
        switch (dbType) {
            case LEVELDB: {
                // grab leveldb specific parameters
                int max_fd_alloc_size = Integer.parseInt(info.getProperty(PROP_MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES)));
                int block_size = Integer.parseInt(info.getProperty(PROP_BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE)));
                int write_buffer_size = Integer.parseInt(info.getProperty(PROP_WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE)));
                int cache_size = Integer.parseInt(info.getProperty(PROP_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE)));

                return new LevelDB(dbName,
                        dbPath,
                        enableDbCache,
                        enableDbCompression,
                        max_fd_alloc_size,
                        block_size,
                        write_buffer_size,
                        cache_size);
            }
            case H2:
                return new H2MVMap(dbName, dbPath, enableDbCache, enableDbCompression);
            default:
                break;
        }

        LOG.error("Invalid database type provided: {}", dbType);
        return null;
    }

    /**
     * @return A database implementation based on a driver implementing the {@link IDriver} interface.
     */
    public static IByteArrayKeyValueDatabase connect(String driverName, Properties info) {
        try {
            // see if the given name is a valid driver
            IDriver driver = ((Class<? extends IDriver>) Class.forName(driverName)).getDeclaredConstructor()
                    .newInstance();
            // return a connection
            return driver.connect(info);
        } catch (Exception e) {
            LOG.error("Could not load database driver.", e);
        }

        LOG.error("Invalid database driver provided: {}", driverName);
        return null;
    }

    /**
     * @return A mock database.
     */
    public static IByteArrayKeyValueDatabase connect(String _dbName) {
        return new MockDB(_dbName);
    }
}
