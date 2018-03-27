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
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.db.impl;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.h2.H2MVMapWithCache;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.leveldb.LevelDBWithCache;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.MockDBWithCache;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.Properties;

public abstract class DatabaseFactory {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private static final String PROP_DB_TYPE = "db_type";

    private static final String PROP_DB_NAME = "db_name";
    private static final String PROP_DB_PATH = "db_path";

    private static final String PROP_ENABLE_AUTO_COMMIT = "enable_auto_commit";
    private static final String PROP_ENABLE_DB_CACHE = "enable_db_cache";
    private static final String PROP_ENABLE_DB_COMPRESSION = "enable_db_compression";
    private static final String PROP_ENABLE_HEAP_CACHE = "enable_heap_cache";

    private static final String PROP_ENABLE_HEAP_CACHE_STATS = "enable_heap_cache_stats";
    private static final String PROP_MAX_HEAP_CACHE_SIZE = "max_heap_cache_size";

    public static final String PROP_MAX_FD_ALLOC = "max_fd_alloc_size";
    public static final String PROP_BLOCK_SIZE = "block_size";

    public static final String PROP_WRITE_BUFFER_SIZE = "write_buffer_size";
    public static final String PROP_CACHE_SIZE = "cache_size";

    public static IByteArrayKeyValueDatabase connect(Properties info) {

        DBVendor dbType = DBVendor.fromString(info.getProperty(PROP_DB_TYPE));

        String dbName = info.getProperty(PROP_DB_NAME);

        boolean enableHeapCache = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE));
        boolean enableAutoCommit = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_AUTO_COMMIT));

        // check for unknown or mock database
        switch (dbType) {
            case UNKNOWN:
                // the driver, if correct should check path and name
                return connect(info.getProperty(PROP_DB_TYPE), info);
            case MOCKDB:
                // don't care about the path value
                if (enableHeapCache) {
                    return new MockDBWithCache(dbName, enableAutoCommit, info.getProperty(PROP_MAX_HEAP_CACHE_SIZE),
                            Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE_STATS)));
                } else {
                    return new MockDB(dbName);
                }
            default:
                break;
        }

        String dbPath = info.getProperty(PROP_DB_PATH);

        boolean enableDbCache = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_DB_CACHE));
        boolean enableDbCompression = Boolean.parseBoolean(info.getProperty(PROP_ENABLE_DB_COMPRESSION));

        // ensure not null path for other databases
        if (dbPath == null) {
            LOG.error("Please provide a database path value that is not null.");
            return null;
        }

        // ensure not null name for other databases
        if (dbName == null) {
            LOG.error("Please provide a database name value that is not null.");
            return null;
        }

        // select database implementation
        switch (dbType) {
            case LEVELDB:
                // grab leveldb specific parameters
                int max_fd_alloc_size = Integer.parseInt(info.getProperty(PROP_MAX_FD_ALLOC));
                int block_size = Integer.parseInt(info.getProperty(PROP_BLOCK_SIZE));
                int write_buffer_size = Integer.parseInt(info.getProperty(PROP_WRITE_BUFFER_SIZE));
                int cache_size = Integer.parseInt(info.getProperty(PROP_CACHE_SIZE));

                if (enableHeapCache) {
                    return new LevelDBWithCache(dbName,
                            dbPath,
                            enableDbCache,
                            enableDbCompression,
                            enableAutoCommit,
                            info.getProperty(PROP_MAX_HEAP_CACHE_SIZE),
                            Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE_STATS)),
                            max_fd_alloc_size,
                            block_size,
                            write_buffer_size,
                            cache_size);
                } else {
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
                if (enableHeapCache) {
                    return new H2MVMapWithCache(dbName, dbPath, enableDbCache, enableDbCompression, enableAutoCommit,
                            info.getProperty(PROP_MAX_HEAP_CACHE_SIZE),
                            Boolean.parseBoolean(info.getProperty(PROP_ENABLE_HEAP_CACHE_STATS)));
                } else {
                    return new H2MVMap(dbName, dbPath, enableDbCache, enableDbCompression);
                }
            default:
                break;
        }

        LOG.error("Invalid database type provided: {}", dbType);
        return null;
    }

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
}