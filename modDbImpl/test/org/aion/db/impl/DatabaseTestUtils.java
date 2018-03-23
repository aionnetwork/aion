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

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

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

    public static Object unlockedDatabaseInstanceDefinitions() {
        return unlockedDatabaseInstanceDefinitionsInternal().toArray();
    }

    public static List<Object> unlockedDatabaseInstanceDefinitionsInternal() {

        Properties sharedProps = new Properties();
        sharedProps.setProperty("db_path", dbPath);

        List<Object> parameters = new ArrayList<>();

        sharedProps.setProperty(DatabaseFactory.PROP_ENABLE_LOCKING, disabled);
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

        return parameters;
    }

    public static Object databaseInstanceDefinitions() {

        List<Object> parameters = new ArrayList<>();
        List<Object> parametersUnlocked = unlockedDatabaseInstanceDefinitionsInternal();

        for (Object prop : parametersUnlocked) {
            Properties p = (Properties) ((Properties) prop).clone();
            p.setProperty(DatabaseFactory.PROP_ENABLE_LOCKING, enabled);

            parameters.add(p);
        }

        parameters.addAll(parametersUnlocked);

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

    /**
     * From <a href="https://github.com/junit-team/junit4/wiki/multithreaded-code-and-concurrency">JUnit Wiki on multithreaded code and concurrency</a>
     */
    public static void assertConcurrent(final String message, final List<? extends Runnable> runnables,
            final int maxTimeoutSeconds) throws InterruptedException {
        final int numThreads = runnables.size();
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
        final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        try {
            final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
                threadPool.submit(() -> {
                    allExecutorThreadsReady.countDown();
                    try {
                        afterInitBlocker.await();
                        submittedTestRunnable.run();
                    } catch (final Throwable e) {
                        exceptions.add(e);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            // wait until all threads are ready
            assertTrue(
                    "Timeout initializing threads! Perform long lasting initializations before passing runnables to assertConcurrent",
                    allExecutorThreadsReady.await(runnables.size() * 10, TimeUnit.MILLISECONDS));
            // start all test runners
            afterInitBlocker.countDown();
            assertTrue(message + " timeout! More than" + maxTimeoutSeconds + "seconds",
                    allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
        } finally {
            threadPool.shutdownNow();
        }
        if (!exceptions.isEmpty()) {
            for (Throwable e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue(message + "failed with " + exceptions.size() + " exception(s):" + exceptions, exceptions.isEmpty());
    }
}
