package org.aion.db.impl;

import static org.aion.db.impl.DatabaseFactory.Props;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.db.utils.MongoTestRunner;

public class DatabaseTestUtils {

    static final String dbName = "TestDB-";
    static final File testDir = new File(System.getProperty("user.dir"), "tmp");
    private static final String dbPath = testDir.getAbsolutePath();
    private static final Set<String> sizeHeapCache = Set.of("0", "256");
    // TODO: [Task AJK-169] re-enable MongoDB tests by adding DBVendor.MONGODB
    private static final Set<DBVendor> vendors =
            Set.of(DBVendor.MOCKDB, DBVendor.H2, DBVendor.LEVELDB, DBVendor.ROCKSDB);
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
        sharedProps.setProperty(Props.DB_PATH, dbPath);

        List<Object> parameters = new ArrayList<>();

        sharedProps.setProperty(Props.ENABLE_LOCKING, disabled);
        // the following parameters are irrelevant
        sharedProps.setProperty(Props.ENABLE_AUTO_COMMIT, enabled);
        sharedProps.setProperty(Props.MAX_HEAP_CACHE_SIZE, "0");
        sharedProps.setProperty(Props.ENABLE_HEAP_CACHE_STATS, disabled);

        // all vendor options
        for (DBVendor vendor : vendors) {
            sharedProps.setProperty(Props.DB_TYPE, vendor.toValue());

            addDatabaseWithCacheAndCompression(vendor, sharedProps, parameters);
        }

        return parameters;
    }

    public static List<Properties> unlockedPersistentDatabaseInstanceDefinitionsInternal() {

        Properties sharedProps = new Properties();
        sharedProps.setProperty(Props.DB_PATH, dbPath);

        List<Properties> parameters = new ArrayList<>();

        sharedProps.setProperty(Props.ENABLE_LOCKING, disabled);
        sharedProps.setProperty(Props.ENABLE_HEAP_CACHE, disabled);

        // all vendor options
        for (DBVendor vendor : Set.of(DBVendor.LEVELDB)) { // , DBVendor.ROCKSDB, DBVendor.H2)) {
            sharedProps.setProperty(Props.DB_TYPE, vendor.toValue());
            sharedProps.setProperty(Props.ENABLE_DB_COMPRESSION, disabled);
            parameters.add((Properties) sharedProps.clone());
            sharedProps.setProperty(Props.ENABLE_DB_COMPRESSION, enabled);
            parameters.add((Properties) sharedProps.clone());
        }

        return parameters;
    }

    public static Object databaseInstanceDefinitions() {

        List<Object> parameters = new ArrayList<>();
        List<Object> parametersUnlocked = unlockedDatabaseInstanceDefinitionsInternal();

        for (Object prop : parametersUnlocked) {
            Properties p = (Properties) ((Properties) prop).clone();
            p.setProperty(Props.ENABLE_LOCKING, enabled);

            parameters.add(p);
        }

        parameters.addAll(parametersUnlocked);

        return parameters.toArray();
    }

    private static void addDatabaseWithCacheAndCompression(
            DBVendor vendor, Properties sharedProps, List<Object> parameters) {

        if (vendor == DBVendor.MONGODB) {
            sharedProps = (Properties) sharedProps.clone();
            sharedProps.setProperty(Props.DB_PATH, MongoTestRunner.inst().getConnectionString());
        }

        if (vendor != DBVendor.MOCKDB) {
            // enable/disable db_cache
            for (String db_cache : options) {
                sharedProps.setProperty(Props.ENABLE_DB_CACHE, db_cache);
                // enable/disable db_compression
                for (String db_compression : options) {
                    sharedProps.setProperty(Props.ENABLE_DB_COMPRESSION, db_compression);

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
     * From <a
     * href="https://github.com/junit-team/junit4/wiki/multithreaded-code-and-concurrency">JUnit
     * Wiki on multithreaded code and concurrency</a>
     */
    public static void assertConcurrent(
            final String message,
            final List<? extends Runnable> runnables,
            final int maxTimeoutSeconds)
            throws InterruptedException {
        final int numThreads = runnables.size();
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        try {
            final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
                threadPool.submit(
                        () -> {
                            allExecutorThreadsReady.countDown();
                            try {
                                afterInitBlocker.await();
                                submittedTestRunnable.run();
                            } catch (final Exception e) {
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
            assertTrue(
                    message + " timeout! More than" + maxTimeoutSeconds + "seconds",
                    allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
        } finally {
            threadPool.shutdownNow();
        }
        if (!exceptions.isEmpty()) {
            for (Throwable e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue(
                message + "failed with " + exceptions.size() + " exception(s):" + exceptions,
                exceptions.isEmpty());
    }

    /**
     * Helper method to find an unused port of the local machine
     *
     * @return An unused port
     */
    public static int findOpenPort() {
        try (ServerSocket socket = new ServerSocket(0); ) {
            return socket.getLocalPort();
        } catch (Exception ex) {
            fail("Exception thrown finding open port: " + ex.getMessage());
        }

        return -1;
    }
}
