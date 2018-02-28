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

import org.aion.db.impl.DBVendor;

import java.io.File;
import java.util.*;

public class DatabaseTestUtils {

    static final String dbName = "TestDB-";
    static final File testDir = new File(System.getProperty("user.dir"), "tmp");
    private static final String dbPath = testDir.getAbsolutePath();
    private static final Set<String> sizeHeapCache = Set.of("0", "256");
    private static final Set<DBVendor> vendors = Set.of(DBVendor.MOCKDB, DBVendor.H2, DBVendor.LEVELDB);
    private static final String enabled = String.valueOf(Boolean.TRUE);
    private static final String disabled = String.valueOf(Boolean.FALSE);
    private static final Set<String> options = Set.of(enabled, disabled);

    private static int count = 0;

    public static synchronized int getNext() {
        count++;
        return count;
    }

    public static Object databaseInstanceDefinitions() {

        Properties sharedProps = new Properties();
        sharedProps.setProperty("db_path", dbPath);

        List<Object> parameters = new ArrayList<>();

        // adding database variations without heap caching
        sharedProps.setProperty("enable_heap_cache", disabled);
        // the following parameters are irrelevant
        sharedProps.setProperty("enable_auto_commit", enabled);
        sharedProps.setProperty("max_heap_cache_size", "0");
        sharedProps.setProperty("max_heap_cache_size", disabled);

        // all vendor options
        for (DBVendor vendor : vendors) {
            sharedProps.setProperty("db_type", vendor.toValue());

            addDatabaseWithCacheAndCompression(vendor, sharedProps, parameters);
        }

        // adding database variations with heap caching
        sharedProps.setProperty("enable_heap_cache", enabled);

        // all vendor options
        for (DBVendor vendor : vendors) {
            sharedProps.setProperty("db_type", vendor.toValue());
            // enable/disable auto_commit
            for (String auto_commit : options) {
                sharedProps.setProperty("enable_auto_commit", auto_commit);
                // unbounded/bounded max_heap_cache_size
                for (String max_heap_cache_size : sizeHeapCache) {
                    sharedProps.setProperty("max_heap_cache_size", max_heap_cache_size);
                    // enable/disable heap_cache_stats
                    for (String heap_cache_stats : options) {
                        sharedProps.setProperty("enable_heap_cache_stats", heap_cache_stats);

                        addDatabaseWithCacheAndCompression(vendor, sharedProps, parameters);
                    }
                }
            }
        }

        // System.out.println(parameters.size());

        return parameters.toArray();
    }

    private static void addDatabaseWithCacheAndCompression(DBVendor vendor, Properties sharedProps,
            List<Object> parameters) {
        if (vendor != DBVendor.MOCKDB) {
            // enable/disable db_cache
            for (String db_cache : options) {
                sharedProps.setProperty("enable_db_cache", db_cache);
                // enable/disable db_compression
                for (String db_compression : options) {
                    sharedProps.setProperty("enable_db_compression", db_compression);

                    // generating new database configuration
                    parameters.add(sharedProps.clone());
                }
            }
        } else {
            // generating new database configuration for MOCKDB
            parameters.add(sharedProps.clone());
        }
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        new Random().nextBytes(result);
        return result;
    }
}
