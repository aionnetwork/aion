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

import com.google.common.truth.Truth;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.generic.DatabaseWithCache;
import org.aion.db.generic.LockedDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.utils.FileUtils;
import org.aion.log.AionLoggerFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

/*
 * Unwritten Tests List:
 * - concurrent access and modification
 * - correct file creation and deletion
 * - disconnect and reconnect
 * - simultaneous connections to the same DB by different threads
 * - released locks after execution with exceptions
 */

/**
 * Base database tests, to be passed by all driver implementations.
 *
 * @author ali
 * @author Alexandra Roatis
 */
@RunWith(Parameterized.class)
public class DriverBaseTest {

    private static final File testDir = new File(System.getProperty("user.dir"), "tmp");
    private static final String dbNamePrefix = "TestDB";
    private static final String dbPath = testDir.getAbsolutePath();
    private static final String unboundHeapCache = "0";
    //    public static String boundHeapCache = "256";

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() throws NoSuchMethodException, SecurityException {
        return Arrays.asList(new Object[][] {
                // H2MVMap wo. db cache wo. compression
                { "H2MVMap", new boolean[] { false, false, false },
                        // { isLocked, isHeapCacheEnabled, isAutocommitEnabled }
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // H2MVMap w. db cache wo. compression
                { "H2MVMap+dbCache", new boolean[] { false, false, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, true, false } },
                // H2MVMap wo. db cache w. compression
                { "H2MVMap+compression", new boolean[] { false, false, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, true } },
                // H2MVMap w. db cache w. compression
                { "H2MVMap+dbCache+compression", new boolean[] { false, false, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, true, true } },
                // LevelDB wo. db cache wo. compression
                { "LevelDB", new boolean[] { false, false, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // LevelDB w. db cache wo. compression
                { "LevelDB+dbCache", new boolean[] { false, false, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, true, false } },
                // LevelDB wo. db cache w. compression
                { "LevelDB+compression", new boolean[] { false, false, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, true } },
                // LevelDB w. db cache w. compression
                { "LevelDB+dbCache+compression", new boolean[] { false, false, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, true, true } },
                // MockDB
                { "MockDB", new boolean[] { false, false, false }, MockDB.class.getDeclaredConstructor(String.class),
                        new Object[] { dbNamePrefix } },
                // H2MVMap
                { "H2MVMap+lock", new boolean[] { true, false, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // LevelDB wo. db cache wo. compression
                { "LevelDB+lock", new boolean[] { true, false, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // MockDB
                { "MockDB+lock", new boolean[] { true, false, false },
                        MockDB.class.getDeclaredConstructor(String.class), new Object[] { dbNamePrefix } },
                // H2MVMap
                { "H2MVMap+heapCache", new boolean[] { false, true, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // LevelDB wo. db cache wo. compression
                { "LevelDB+heapCache", new boolean[] { false, true, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // MockDB
                { "MockDB+heapCache", new boolean[] { false, true, false },
                        MockDB.class.getDeclaredConstructor(String.class), new Object[] { dbNamePrefix } },
                // H2MVMap
                { "H2MVMap+heapCache+lock", new boolean[] { true, true, false },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // LevelDB wo. db cache wo. compression
                { "LevelDB+heapCache+lock", new boolean[] { true, true, false },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // MockDB
                { "MockDB+heapCache+lock", new boolean[] { true, true, false },
                        MockDB.class.getDeclaredConstructor(String.class), new Object[] { dbNamePrefix } },
                // H2MVMap
                { "H2MVMap+heapCache+autocommit", new boolean[] { false, true, true },
                        H2MVMap.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // LevelDB wo. db cache wo. compression
                { "LevelDB+heapCache+autocommit", new boolean[] { false, true, true },
                        LevelDB.class.getDeclaredConstructor(String.class, String.class, boolean.class, boolean.class),
                        new Object[] { dbNamePrefix + DatabaseTestUtils.getNext(), dbPath, false, false } },
                // MockDB
                { "MockDB+heapCache+autocommit", new boolean[] { false, true, true },
                        MockDB.class.getDeclaredConstructor(String.class), new Object[] { dbNamePrefix } } });
    }

    private IByteArrayKeyValueDatabase db;

    private final Constructor<IByteArrayKeyValueDatabase> constructor;
    private final Object[] args;
    private final String dbName;

    private static final byte[] k1 = "key1".getBytes();
    private static final byte[] v1 = "value1".getBytes();

    private static final byte[] k2 = "key2".getBytes();
    private static final byte[] v2 = "value2".getBytes();

    private static final byte[] k3 = "key3".getBytes();
    private static final byte[] v3 = "value3".getBytes();

    /**
     * Every test invocation instantiates a new IByteArrayKeyValueDB
     */
    public DriverBaseTest(String testName, boolean[] props, Constructor<IByteArrayKeyValueDatabase> constructor,
            Object[] args)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "TRACE");

        AionLoggerFactory.init(cfg);

        this.constructor = constructor;
        this.args = args;
        this.dbName = (String) args[0];
        this.db = constructor.newInstance(args);
        if (props[1]) {
            this.db = new DatabaseWithCache((AbstractDB) this.db, props[2], "0", false);
        }
        if (props[0]) {
            this.db = new LockedDatabase(this.db);
        }
    }

    @BeforeClass
    public static void setup() {
        // clean out the tmp directory
        Truth.assertThat(FileUtils.deleteRecursively(testDir)).isTrue();
        assertThat(testDir.mkdirs()).isTrue();
    }

    @AfterClass
    public static void teardown() {
        assertThat(testDir.delete()).isTrue();
    }

    @Before
    public void open() {
        assertThat(db).isNotNull();
        assertThat(db.isOpen()).isFalse();
        assertThat(db.isClosed()).isTrue();

        if (db.isPersistent()) {
            assertThat(db.isCreatedOnDisk()).isFalse();
            assertThat(db.getPath().get()).isEqualTo(new File(dbPath, dbName).getAbsolutePath());
        }

        assertThat(db.isLocked()).isFalse();
        assertThat(db.getName().get()).isEqualTo(dbName);

        assertThat(db.open()).isTrue();

        assertThat(db.isOpen()).isTrue();
        assertThat(db.isClosed()).isFalse();
        assertThat(db.isEmpty()).isTrue();

        if (db.isPersistent()) {
            assertThat(db.isCreatedOnDisk()).isTrue();
            assertThat(db.getPath().get()).isEqualTo(new File(dbPath, dbName).getAbsolutePath());
        }

        assertThat(db.isLocked()).isFalse();
        assertThat(db.getName().get()).isEqualTo(dbName);
    }

    @After
    public void close() {
        assertThat(db).isNotNull();
        assertThat(db.isOpen()).isTrue();
        assertThat(db.isClosed()).isFalse();

        if (db.isPersistent()) {
            assertThat(db.isCreatedOnDisk()).isTrue();
            assertThat(db.getPath().get()).isEqualTo(new File(dbPath, dbName).getAbsolutePath());
        }

        assertThat(db.isLocked()).isFalse();
        assertThat(db.getName().get()).isEqualTo(dbName);

        db.close();

        assertThat(db.isOpen()).isFalse();
        assertThat(db.isClosed()).isTrue();

        // for non-persistent DB's, close() should wipe the DB
        if (db.isPersistent()) {
            assertThat(db.isCreatedOnDisk()).isTrue();
            assertThat(FileUtils.deleteRecursively(new File(db.getPath().get()))).isTrue();
            assertThat(db.isCreatedOnDisk()).isFalse();
            assertThat(db.getPath().get()).isEqualTo(new File(dbPath, dbName).getAbsolutePath());
        }

        assertThat(db.isLocked()).isFalse();
        assertThat(db.getName().get()).isEqualTo(dbName);
    }

    @Test
    public void testConcurrentAccess() {
        // TODO: import test from legacy test case
    }

    @Test
    public void testOpenSecondInstance()
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (db.isPersistent()) {
            // another connection to same DB should fail on open for all persistent KVDBs
            IByteArrayKeyValueDatabase otherDatabase = this.constructor.newInstance(this.args);
            assertThat(otherDatabase.open()).isFalse();

            // ensuring that new connection did not somehow close old one
            assertThat(db.isOpen()).isTrue();
            assertThat(db.isLocked()).isFalse();
        }
    }

    @Test
    public void testPersistence() throws InterruptedException {
        if (db.isPersistent()) {
            // adding data ---------------------------------------------------------------------------------------------
            assertThat(db.get(k1).isPresent()).isFalse();
            db.put(k1, v1);
            assertThat(db.isLocked()).isFalse();

            // commit, close & reopen
            if (!db.isAutoCommitEnabled()) { db.commit(); }

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure persistence
            assertThat(db.get(k1).get()).isEqualTo(v1);
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(1);
            assertThat(db.isLocked()).isFalse();

            // deleting data -------------------------------------------------------------------------------------------
            db.delete(k1);
            assertThat(db.isLocked()).isFalse();

            // commit, close & reopen
            if (!db.isAutoCommitEnabled()) { db.commit(); }

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure absence
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.isEmpty()).isTrue();
            assertThat(db.keys().size()).isEqualTo(0);
            assertThat(db.isLocked()).isFalse();
        }
    }

    @Test
    public void testBatchPersistence() throws InterruptedException {
        if (db.isPersistent()) {
            // adding data ---------------------------------------------------------------------------------------------
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.get(k2).isPresent()).isFalse();
            assertThat(db.get(k3).isPresent()).isFalse();

            Map<byte[], byte[]> map = new HashMap<>();
            map.put(k1, v1);
            map.put(k2, v2);
            map.put(k3, v3);
            db.putBatch(map);

            assertThat(db.isLocked()).isFalse();

            // commit, close & reopen
            if (!db.isAutoCommitEnabled()) { db.commit(); }

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure persistence
            assertThat(db.get(k1).get()).isEqualTo(v1);
            assertThat(db.get(k2).get()).isEqualTo(v2);
            assertThat(db.get(k3).get()).isEqualTo(v3);
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(3);
            assertThat(db.isLocked()).isFalse();

            // updating data -------------------------------------------------------------------------------------------
            map.clear();
            map.put(k1, v2);
            map.put(k2, v3);
            map.put(k3, null);
            db.putBatch(map);

            assertThat(db.isLocked()).isFalse();

            // commit, close & reopen
            if (!db.isAutoCommitEnabled()) { db.commit(); }

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure absence
            assertThat(db.get(k1).get()).isEqualTo(v2);
            assertThat(db.get(k2).get()).isEqualTo(v3);
            assertThat(db.get(k3).isPresent()).isFalse();
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(2);
            assertThat(db.isLocked()).isFalse();

            // deleting data -------------------------------------------------------------------------------------------
            db.deleteBatch(map.keySet());

            assertThat(db.isLocked()).isFalse();

            // commit, close & reopen
            if (!db.isAutoCommitEnabled()) { db.commit(); }

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure absence
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.get(k2).isPresent()).isFalse();
            assertThat(db.get(k3).isPresent()).isFalse();
            assertThat(db.isEmpty()).isTrue();
            assertThat(db.keys().size()).isEqualTo(0);
            assertThat(db.isLocked()).isFalse();
        }
    }

    @Test
    public void testPut() {
        assertThat(db.get(k1).isPresent()).isFalse();

        db.put(k1, v1);

        assertThat(db.get(k1).get()).isEqualTo(v1);

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testPutBatch() {
        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        db.putBatch(map);

        assertThat(v1).isEqualTo(db.get(k1).get());
        assertThat(v2).isEqualTo(db.get(k2).get());

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testUpdate() {
        // ensure existence
        assertThat(db.get(k1).isPresent()).isFalse();
        db.put(k1, v1);

        assertThat(v1).isEqualTo(db.get(k1).get());

        // check after update
        db.put(k1, v2);

        assertThat(v2).isEqualTo(db.get(k1).get());

        // check after indirect delete
        db.put(k1, null);

        assertThat(db.get(k1).isPresent()).isFalse();

        // check after direct delete
        db.delete(k1);

        assertThat(db.get(k1).isPresent()).isFalse();

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testUpdateBatch() {
        // ensure existence
        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();
        assertThat(db.get(k3).isPresent()).isFalse();
        db.put(k1, v1);
        db.put(k2, v2);

        assertThat(v1).isEqualTo(db.get(k1).get());
        assertThat(v2).isEqualTo(db.get(k2).get());

        // check after update
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v1);
        ops.put(k3, v3);
        db.putBatch(ops);

        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(v1).isEqualTo(db.get(k2).get());
        assertThat(v3).isEqualTo(db.get(k3).get());

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testDelete() {
        // ensure existence
        db.put(k1, v1);

        assertThat(db.get(k1).isPresent()).isTrue();

        // check presence after delete
        db.delete(k1);

        assertThat(db.get(k1).isPresent()).isFalse();

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testDeleteBatch() {
        // ensure existence
        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, null);
        db.putBatch(map);

        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.get(k3).isPresent()).isFalse();

        // check presence after delete
        db.deleteBatch(map.keySet());

        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();
        assertThat(db.get(k3).isPresent()).isFalse();

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }


    @Test
    public void testDrop() {
        // ensure existence
        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, null);
        db.putBatch(map);

        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.get(k3).isPresent()).isFalse();

        // check presence after delete
        db.drop();

        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();
        assertThat(db.get(k3).isPresent()).isFalse();

        // ensure unlocked
        assertThat(db.isLocked()).isFalse();
    }

    @Test
    public void testApproximateDBSize() {
        if (db.isPersistent()) {
            int repeat = 1_000_000;
            for (int i = 0; i < repeat; i++) {
                db.put(String.format("%c%09d", 'a' + i % 26, i).getBytes(), "test".getBytes());
            }
            // estimate
            long est = db.approximateSize();
            long count = FileUtils.getDirectorySizeBytes(db.getPath().get());

            double error = Math.abs(1.0 * (est - count) / count);
            assertTrue(error < 0.5);
        } else {
            assertTrue(db.approximateSize() == -1L);
        }
    }

    @Test
    public void testKeys() {
        // keys shouldn't be null even when empty
        Set<byte[]> keys = db.keys();
        assertThat(db.isLocked()).isFalse();
        assertThat(db.isEmpty()).isTrue();
        assertThat(keys).isNotNull();
        assertThat(keys.size()).isEqualTo(0);

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        keys = db.keys();
        assertThat(db.isLocked()).isFalse();
        assertThat(keys.size()).isEqualTo(2);

        // because of byte[], set.contains() does not work as expected
        int count = 0;
        for (byte[] k : keys) {
            if (Arrays.equals(k1, k) || Arrays.equals(k2, k)) {
                count++;
            }
        }
        assertThat(count).isEqualTo(2);

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isFalse();

        keys = db.keys();
        assertThat(db.isLocked()).isFalse();
        assertThat(keys.size()).isEqualTo(1);

        count = 0;
        for (byte[] k : keys) {
            if (Arrays.equals(k1, k)) {
                count++;
            }
        }
        assertThat(count).isEqualTo(1);

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);

        keys = db.keys();
        assertThat(db.isLocked()).isFalse();
        assertThat(keys.size()).isEqualTo(2);

        count = 0;
        for (byte[] k : keys) {
            if (Arrays.equals(k2, k) || Arrays.equals(k3, k)) {
                count++;
            }
        }
        assertThat(count).isEqualTo(2);

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());

        keys = db.keys();
        assertThat(db.isLocked()).isFalse();
        assertThat(keys.size()).isEqualTo(0);
    }

    @Test
    public void testIsEmpty() {
        assertThat(db.isEmpty()).isTrue();
        assertThat(db.isLocked()).isFalse();

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        assertThat(db.isEmpty()).isFalse();
        assertThat(db.isLocked()).isFalse();

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isFalse();

        assertThat(db.isEmpty()).isFalse();
        assertThat(db.isLocked()).isFalse();

        db.delete(k1);

        assertThat(db.isEmpty()).isTrue();
        assertThat(db.isLocked()).isFalse();

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);

        assertThat(db.isEmpty()).isFalse();
        assertThat(db.isLocked()).isFalse();

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());

        assertThat(db.isEmpty()).isTrue();
        assertThat(db.isLocked()).isFalse();
    }

    /**
     * Checks that data does not persist without explicit commits.
     */
    @Test
    public void testAutoCommitDisabled() throws InterruptedException {
        if (db.isPersistent() && !db.isAutoCommitEnabled()) {
            // adding data ---------------------------------------------------------------------------------------------
            assertThat(db.get(k1).isPresent()).isFalse();
            db.put(k1, v1);
            assertThat(db.isLocked()).isFalse();

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure lack of persistence
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.isEmpty()).isTrue();
            assertThat(db.keys().size()).isEqualTo(0);
            assertThat(db.isLocked()).isFalse();

            // deleting data -------------------------------------------------------------------------------------------
            db.put(k1, v1);
            db.commit();
            assertThat(db.isLocked()).isFalse();

            db.delete(k1);
            assertThat(db.isLocked()).isFalse();

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure lack of persistence of delete
            assertThat(db.get(k1).get()).isEqualTo(v1);
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(1);
            assertThat(db.isLocked()).isFalse();

            // batch update --------------------------------------------------------------------------------------------
            Map<byte[], byte[]> map = new HashMap<>();
            map.put(k1, null);
            map.put(k2, v2);
            map.put(k3, v3);
            db.putBatch(map);
            db.commit();
            assertThat(db.isLocked()).isFalse();

            map.clear();
            map.put(k1, v2);
            map.put(k2, v3);
            map.put(k3, null);
            db.putBatch(map);
            assertThat(db.isLocked()).isFalse();

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure lack of persistence of second update
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.get(k2).get()).isEqualTo(v2);
            assertThat(db.get(k3).get()).isEqualTo(v3);
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(2);
            assertThat(db.isLocked()).isFalse();

            // batch delete --------------------------------------------------------------------------------------------
            db.deleteBatch(map.keySet());
            assertThat(db.isLocked()).isFalse();

            db.close();
            Thread.sleep(100);

            assertThat(db.isClosed()).isTrue();
            assertThat(db.open()).isTrue();

            // ensure lack of persistence of batch delete
            assertThat(db.get(k1).isPresent()).isFalse();
            assertThat(db.get(k2).get()).isEqualTo(v2);
            assertThat(db.get(k3).get()).isEqualTo(v3);
            assertThat(db.isEmpty()).isFalse();
            assertThat(db.keys().size()).isEqualTo(2);
            assertThat(db.isLocked()).isFalse();
        }
    }

}
