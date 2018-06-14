/* ******************************************************************************
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
package org.aion.mcf.trie;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DatabaseFactory;
import org.aion.log.AionLoggerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class JournalPruneDataSourceTest {

    private static final String dbName = "TestDB";
    private static IByteArrayKeyValueDatabase source_db = DatabaseFactory.connect(dbName);
    private static JournalPruneDataSource db;

    private static final byte[] k1 = "key1".getBytes();
    private static final byte[] v1 = "value1".getBytes();

    private static final byte[] k2 = "key2".getBytes();
    private static final byte[] v2 = "value2".getBytes();

    private static final byte[] k3 = "key3".getBytes();
    private static final byte[] v3 = "value3".getBytes();

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "INFO");

        AionLoggerFactory.init(cfg);
    }

    @Before
    public void open() {
        assertThat(source_db.open()).isTrue();
        db = new JournalPruneDataSource(source_db);
    }

    @After
    public void close() {
        source_db.close();
        assertThat(source_db.isClosed()).isTrue();
    }

    // Pruning disabled tests ----------------------------------------------------

    @Test
    public void testPut_woPrune() {
        db.setPruneEnabled(false);

        assertThat(db.get(k1).isPresent()).isFalse();
        db.put(k1, v1);
        assertThat(db.get(k1).get()).isEqualTo(v1);

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);

        // ensure the insert was propagated
        assertThat(source_db.get(k1).get()).isEqualTo(v1);
    }

    @Test
    public void testPutBatch_woPrune() {
        db.setPruneEnabled(false);

        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        db.putBatch(map);

        assertThat(v1).isEqualTo(db.get(k1).get());
        assertThat(v2).isEqualTo(db.get(k2).get());

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);

        // ensure the inserts were propagated
        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
    }

    @Test
    public void testUpdate_woPrune() {
        db.setPruneEnabled(false);

        // insert
        assertThat(db.get(k1).isPresent()).isFalse();
        db.put(k1, v1);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k1).get()).isEqualTo(v1);

        // update
        db.put(k1, v2);
        assertThat(db.get(k1).get()).isEqualTo(v2);
        assertThat(source_db.get(k1).get()).isEqualTo(v2);

        // indirect delete
        db.put(k1, null);
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k1).isPresent()).isTrue();

        // direct delete
        db.put(k2, v2);
        assertThat(db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(source_db.get(k2).isPresent()).isTrue();

        // ensure no cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testUpdateBatch_woPrune() {
        db.setPruneEnabled(false);

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

        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).get()).isEqualTo(v1);
        assertThat(db.get(k3).get()).isEqualTo(v3);

        assertThat(source_db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k2).get()).isEqualTo(v1);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // ensure no cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testDelete_woPrune() {
        db.setPruneEnabled(false);

        // ensure existence
        db.put(k1, v1);
        assertThat(db.get(k1).isPresent()).isTrue();

        // delete not propagated
        db.delete(k1);
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k1).get()).isEqualTo(v1);

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testDeleteBatch_woPrune() {
        db.setPruneEnabled(false);

        // ensure existence
        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, null);
        db.putBatch(map);

        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.get(k3).isPresent()).isFalse();

        // deletes not propagated
        db.deleteBatch(map.keySet());
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.get(k3).isPresent()).isFalse();

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testKeys_woPrune() {
        db.setPruneEnabled(false);

        // keys shouldn't be null even when empty
        Set<byte[]> keys = db.keys();
        assertThat(db.isEmpty()).isTrue();
        assertThat(keys).isNotNull();
        assertThat(keys.size()).isEqualTo(0);

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(2);

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(2);

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(3);

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(3);

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testIsEmpty_woPrune() {
        db.setPruneEnabled(false);

        assertThat(db.isEmpty()).isTrue();

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        assertThat(db.isEmpty()).isFalse();

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.isEmpty()).isFalse();
        db.delete(k1);
        assertThat(db.isEmpty()).isFalse();

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);
        assertThat(db.isEmpty()).isFalse();

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());
        assertThat(db.isEmpty()).isFalse();

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    // Pruning enabled tests ----------------------------------------------------

    private static final byte[] b0 = "block0".getBytes();

    @Test
    public void testPut_wPrune() {
        db.setPruneEnabled(true);

        assertThat(db.get(k1).isPresent()).isFalse();
        db.put(k1, v1);
        assertThat(db.get(k1).get()).isEqualTo(v1);

        // ensure cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);

        // ensure the insert was propagated
        assertThat(source_db.get(k1).get()).isEqualTo(v1);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testPutBatch_wPrune() {
        db.setPruneEnabled(true);

        assertThat(db.get(k1).isPresent()).isFalse();
        assertThat(db.get(k2).isPresent()).isFalse();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        db.putBatch(map);

        assertThat(v1).isEqualTo(db.get(k1).get());
        assertThat(v2).isEqualTo(db.get(k2).get());

        // ensure cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(2);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);

        // ensure the inserts were propagated
        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testUpdate_wPrune() {
        db.setPruneEnabled(true);

        // insert
        assertThat(db.get(k1).isPresent()).isFalse();
        db.put(k1, v1);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k1).get()).isEqualTo(v1);

        // update
        db.put(k1, v2);
        assertThat(db.get(k1).get()).isEqualTo(v2);
        assertThat(source_db.get(k1).get()).isEqualTo(v2);

        // indirect delete
        db.put(k1, null);
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k1).isPresent()).isTrue();

        // direct delete
        db.put(k2, v2);
        assertThat(db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(source_db.get(k2).isPresent()).isTrue();

        // ensure cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(2);
        assertThat(db.getInsertedKeysCount()).isEqualTo(2);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testUpdateBatch_wPrune() {
        db.setPruneEnabled(true);

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

        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).get()).isEqualTo(v1);
        assertThat(db.get(k3).get()).isEqualTo(v3);

        assertThat(source_db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k2).get()).isEqualTo(v1);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // ensure cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        assertThat(db.getInsertedKeysCount()).isEqualTo(3);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testDelete_wPrune() {
        db.setPruneEnabled(true);

        // ensure existence
        db.put(k1, v1);
        assertThat(db.get(k1).isPresent()).isTrue();

        // delete not propagated
        db.delete(k1);
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(source_db.get(k1).get()).isEqualTo(v1);

        // ensure cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testDeleteBatch_wPrune() {
        db.setPruneEnabled(true);

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

        // delete operations are delayed till pruning is called
        assertThat(db.get(k1).isPresent()).isTrue();
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.get(k3).isPresent()).isFalse();

        // ensure cached values
        assertThat(db.getDeletedKeysCount()).isEqualTo(3);
        assertThat(db.getInsertedKeysCount()).isEqualTo(2);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testKeys_wPrune() {
        db.setPruneEnabled(true);

        // keys shouldn't be null even when empty
        Set<byte[]> keys = db.keys();
        assertThat(db.isEmpty()).isTrue();
        assertThat(keys).isNotNull();
        assertThat(keys.size()).isEqualTo(0);

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(2);

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(2);

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(3);

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());

        keys = db.keys();
        assertThat(keys.size()).isEqualTo(3);

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(3);
        assertThat(db.getDeletedKeysCount()).isEqualTo(3);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    @Test
    public void testIsEmpty_wPrune() {
        db.setPruneEnabled(true);

        assertThat(db.isEmpty()).isTrue();

        // checking after put
        db.put(k1, v1);
        db.put(k2, v2);
        assertThat(db.get(k1).get()).isEqualTo(v1);
        assertThat(db.get(k2).get()).isEqualTo(v2);

        assertThat(db.isEmpty()).isFalse();

        // checking after delete
        db.delete(k2);
        assertThat(db.get(k2).isPresent()).isTrue();
        assertThat(db.isEmpty()).isFalse();
        db.delete(k1);
        assertThat(db.isEmpty()).isFalse();

        // checking after putBatch
        Map<byte[], byte[]> ops = new HashMap<>();
        ops.put(k1, null);
        ops.put(k2, v2);
        ops.put(k3, v3);
        db.putBatch(ops);
        assertThat(db.isEmpty()).isFalse();

        // checking after deleteBatch
        db.deleteBatch(ops.keySet());
        assertThat(db.isEmpty()).isFalse();

        // ensure no cached values
        assertThat(db.getInsertedKeysCount()).isEqualTo(3);
        assertThat(db.getDeletedKeysCount()).isEqualTo(3);

        // check store block
        db.storeBlockChanges(b0, 0);
        assertThat(db.getInsertedKeysCount()).isEqualTo(0);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
    }

    // Access with exception tests ----------------------------------------------------

    @Test(expected = RuntimeException.class)
    public void testIsEmpty_wClosedDatabase() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt isEmpty on closed db
        db.isEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void testIsEmpty_wClosedDatabase_wInsertedKeys() {
        db.setPruneEnabled(true);
        db.put(randomBytes(32), randomBytes(32));

        source_db.close();
        assertThat(source_db.isOpen()).isFalse();
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);

        // attempt isEmpty on closed db
        db.isEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void testKeys_wClosedDatabase() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt keys on closed db
        db.keys();
    }

    @Test(expected = RuntimeException.class)
    public void testGet_wClosedDatabase() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt get on closed db
        db.get(randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    public void testPut_wClosedDatabase() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt put on closed db
        db.put(randomBytes(32), randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    public void testPut_wClosedDatabase_wNullValue() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt put on closed db
        db.put(randomBytes(32), null);
    }

    @Test(expected = RuntimeException.class)
    public void testDelete_wClosedDatabase_wPrune() {
        db.setPruneEnabled(true);
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt delete on closed db
        db.delete(randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    public void testDelete_wClosedDatabase_woPrune() {
        db.setPruneEnabled(false);
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        // attempt delete on closed db
        db.delete(randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    public void testPutBatch_wClosedDatabase() {
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(randomBytes(32), randomBytes(32));
        map.put(randomBytes(32), randomBytes(32));
        map.put(randomBytes(32), randomBytes(32));

        // attempt putBatch on closed db
        db.putBatch(map);
    }

    @Test(expected = RuntimeException.class)
    public void testDeleteBatch_wClosedDatabase_wPrune() {
        db.setPruneEnabled(true);
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        List<byte[]> list = new ArrayList<>();
        list.add(randomBytes(32));
        list.add(randomBytes(32));
        list.add(randomBytes(32));

        // attempt deleteBatch on closed db
        db.deleteBatch(list);
    }

    @Test(expected = RuntimeException.class)
    public void testDeleteBatch_wClosedDatabase_woPrune() {
        db.setPruneEnabled(false);
        source_db.close();
        assertThat(source_db.isOpen()).isFalse();

        List<byte[]> list = new ArrayList<>();
        list.add(randomBytes(32));
        list.add(randomBytes(32));
        list.add(randomBytes(32));

        // attempt deleteBatch on closed db
        db.deleteBatch(list);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGet_wNullKey() {
        assertThat(source_db.open()).isTrue();

        // attempt get with null key
        db.get(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPut_wNullKey() {
        assertThat(source_db.open()).isTrue();

        // attempt put with null key
        db.put(null, randomBytes(32));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDelete_wNullKey() {
        assertThat(source_db.open()).isTrue();

        // attempt delete with null key
        db.delete(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutBatch_wNullKey() {
        assertThat(source_db.open()).isTrue();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(randomBytes(32), randomBytes(32));
        map.put(randomBytes(32), randomBytes(32));
        map.put(null, randomBytes(32));

        // attempt putBatch on closed db
        db.putBatch(map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteBatch_wNullKey() {
        assertThat(source_db.open()).isTrue();

        List<byte[]> list = new ArrayList<>();
        list.add(randomBytes(32));
        list.add(randomBytes(32));
        list.add(null);

        // attempt deleteBatch on closed db
        db.deleteBatch(list);
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        new Random().nextBytes(result);
        return result;
    }

    // Concurrent access tests ----------------------------------------------------

    private static final int CONCURRENT_THREADS = 200;
    private static final int TIME_OUT = 100; // in seconds
    private static final boolean DISPLAY_MESSAGES = false;

    private void addThread_IsEmpty(List<Runnable> threads, JournalPruneDataSource db) {
        threads.add(
                () -> {
                    boolean check = db.isEmpty();
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": "
                                        + (check ? "EMPTY" : "NOT EMPTY"));
                    }
                });
    }

    private void addThread_Keys(List<Runnable> threads, JournalPruneDataSource db) {
        threads.add(
                () -> {
                    Set<byte[]> keys = db.keys();
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName() + ": #keys = " + keys.size());
                    }
                });
    }

    private void addThread_Get(List<Runnable> threads, JournalPruneDataSource db, String key) {
        threads.add(
                () -> {
                    boolean hasValue = db.get(key.getBytes()).isPresent();
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": "
                                        + key
                                        + " "
                                        + (hasValue ? "PRESENT" : "NOT PRESENT"));
                    }
                });
    }

    private void addThread_Put(List<Runnable> threads, JournalPruneDataSource db, String key) {
        threads.add(
                () -> {
                    db.put(key.getBytes(), randomBytes(32));
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName() + ": " + key + " ADDED");
                    }
                });
    }

    private void addThread_Delete(List<Runnable> threads, JournalPruneDataSource db, String key) {
        threads.add(
                () -> {
                    db.delete(key.getBytes());
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName() + ": " + key + " DELETED");
                    }
                });
    }

    private void addThread_PutBatch(List<Runnable> threads, JournalPruneDataSource db, String key) {
        threads.add(
                () -> {
                    Map<byte[], byte[]> map = new HashMap<>();
                    map.put((key + 1).getBytes(), randomBytes(32));
                    map.put((key + 2).getBytes(), randomBytes(32));
                    map.put((key + 3).getBytes(), randomBytes(32));
                    db.putBatch(map);
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": "
                                        + (key + 1)
                                        + ", "
                                        + (key + 2)
                                        + ", "
                                        + (key + 3)
                                        + " ADDED");
                    }
                });
    }

    private void addThread_DeleteBatch(
            List<Runnable> threads, JournalPruneDataSource db, String key) {
        threads.add(
                () -> {
                    List<byte[]> list = new ArrayList<>();
                    list.add((key + 1).getBytes());
                    list.add((key + 2).getBytes());
                    list.add((key + 3).getBytes());
                    db.deleteBatch(list);
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": "
                                        + (key + 1)
                                        + ", "
                                        + (key + 2)
                                        + ", "
                                        + (key + 3)
                                        + " DELETED");
                    }
                });
    }

    private void addThread_StoreBlockChanges(
            List<Runnable> threads, JournalPruneDataSource db, String hash, long number) {
        threads.add(
                () -> {
                    db.storeBlockChanges(hash.getBytes(), number);
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": block ("
                                        + hash
                                        + ", "
                                        + number
                                        + ") STORED");
                    }
                });
    }

    private void addThread_Prune(
            List<Runnable> threads, JournalPruneDataSource db, String hash, long number) {
        threads.add(
                () -> {
                    db.prune(hash.getBytes(), number);
                    if (DISPLAY_MESSAGES) {
                        System.out.println(
                                Thread.currentThread().getName()
                                        + ": block ("
                                        + hash
                                        + ", "
                                        + number
                                        + ") PRUNED");
                    }
                });
    }

    @Test
    public void testConcurrentAccessOnOpenDatabase() throws InterruptedException {
        assertThat(source_db.isOpen()).isTrue();
        db.setPruneEnabled(true);

        // create distinct threads with
        List<Runnable> threads = new ArrayList<>();

        int threadSetCount = CONCURRENT_THREADS / 8;
        if (threadSetCount < 3) {
            threadSetCount = 3;
        }

        String keyStr, blockStr;

        for (int i = 0; i < threadSetCount; i++) {
            // 1. thread that checks empty
            addThread_IsEmpty(threads, db);

            // 2. thread that gets keys
            addThread_Keys(threads, db);

            keyStr = "key-" + i + ".";

            // 3. thread that gets entry
            addThread_Get(threads, db, keyStr);

            // 4. thread that puts entry
            addThread_Put(threads, db, keyStr);

            // 5. thread that deletes entry
            addThread_Delete(threads, db, keyStr);

            // 6. thread that puts entries
            addThread_PutBatch(threads, db, keyStr);

            // 7. thread that deletes entry
            addThread_DeleteBatch(threads, db, keyStr);

            blockStr = "block-" + i + ".";

            // 8. thread that stores block changes
            addThread_StoreBlockChanges(threads, db, blockStr, i);

            // 9. thread that prunes
            addThread_Prune(threads, db, blockStr, i);
        }

        // run threads and check for exceptions
        assertConcurrent("Testing concurrent access. ", threads, TIME_OUT);

        // ensuring close
        db.close();
        assertThat(source_db.isClosed()).isTrue();
    }

    @Test
    public void testConcurrentPut() throws InterruptedException {
        assertThat(source_db.isOpen()).isTrue();
        db.setPruneEnabled(true);

        // create distinct threads with
        List<Runnable> threads = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            addThread_Put(threads, db, "key-" + i);
        }

        // run threads
        assertConcurrent("Testing put(...) ", threads, TIME_OUT);

        // check that all values were added
        assertThat(db.keys().size()).isEqualTo(CONCURRENT_THREADS);

        // ensuring close
        db.close();
        assertThat(source_db.isClosed()).isTrue();
    }

    @Test
    public void testConcurrentPutBatch() throws InterruptedException {
        assertThat(source_db.isOpen()).isTrue();
        db.setPruneEnabled(true);

        // create distinct threads with
        List<Runnable> threads = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            addThread_PutBatch(threads, db, "key-" + i);
        }

        // run threads
        assertConcurrent("Testing putBatch(...) ", threads, TIME_OUT);

        // check that all values were added
        assertThat(db.keys().size()).isEqualTo(3 * CONCURRENT_THREADS);

        // ensuring close
        db.close();
        assertThat(source_db.isClosed()).isTrue();
    }

    @Test
    public void testConcurrentUpdate() throws InterruptedException {
        assertThat(source_db.isOpen()).isTrue();
        db.setPruneEnabled(true);

        // create distinct threads with
        List<Runnable> threads = new ArrayList<>();

        int threadSetCount = CONCURRENT_THREADS / 4;
        if (threadSetCount < 3) {
            threadSetCount = 3;
        }

        String keyStr, blockStr;

        for (int i = 0; i < threadSetCount; i++) {
            keyStr = "key-" + i + ".";

            // 1. thread that puts entry
            addThread_Put(threads, db, keyStr);

            // 2. thread that deletes entry
            addThread_Delete(threads, db, keyStr);

            // 3. thread that puts entries
            addThread_PutBatch(threads, db, keyStr);

            // 4. thread that deletes entry
            addThread_DeleteBatch(threads, db, keyStr);

            blockStr = "block-" + i + ".";

            // 5. thread that stores block changes
            addThread_StoreBlockChanges(threads, db, blockStr, i);

            // 6. thread that prunes
            addThread_Prune(threads, db, blockStr, i);
        }

        // run threads and check for exceptions
        assertConcurrent("Testing concurrent updates. ", threads, TIME_OUT);

        // ensuring close
        db.close();
        assertThat(source_db.isClosed()).isTrue();
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
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
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

    // Pruning tests ----------------------------------------------------

    private static final byte[] b1 = "block1".getBytes();
    private static final byte[] b2 = "block2".getBytes();
    private static final byte[] b3 = "block3".getBytes();

    private static final byte[] k4 = "key4".getBytes();
    private static final byte[] v4 = "value4".getBytes();

    private static final byte[] k5 = "key5".getBytes();
    private static final byte[] v5 = "value5".getBytes();

    private static final byte[] k6 = "key6".getBytes();
    private static final byte[] v6 = "value6".getBytes();

    @Test
    public void pruningTest() {
        db.setPruneEnabled(true);

        // block 0
        db.put(k1, v1);
        db.put(k2, v2);
        db.put(k3, v3);
        assertThat(db.getInsertedKeysCount()).isEqualTo(3);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
        db.storeBlockChanges(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // block 1
        db.put(k4, v4);
        db.delete(k2);
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k2).isPresent()).isTrue();
        assertThat(source_db.get(k4).get()).isEqualTo(v4);

        // block 2
        db.put(k2, v3);
        db.delete(k3);
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isTrue();

        // block 3
        db.put(k5, v5);
        db.put(k6, v6);
        db.delete(k2);
        assertThat(db.getInsertedKeysCount()).isEqualTo(2);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(4);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isTrue();

        // prune block 0
        db.prune(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // prune block 1
        db.prune(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k4).get()).isEqualTo(v4);
        // not deleted due to block 2 insert
        assertThat(source_db.get(k2).get()).isEqualTo(v3);

        // prune block 2
        db.prune(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isFalse();

        // prune block 3
        db.prune(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(0);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isFalse();
    }

    @Test
    public void pruningTest_wBatch() {
        db.setPruneEnabled(true);

        Map<byte[], byte[]> map = new HashMap<>();

        // block 0
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        db.putBatch(map);

        assertThat(db.getInsertedKeysCount()).isEqualTo(3);
        assertThat(db.getDeletedKeysCount()).isEqualTo(0);
        db.storeBlockChanges(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // block 1
        map.clear();
        map.put(k4, v4);
        map.put(k2, null);
        db.putBatch(map);

        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k2).isPresent()).isTrue();
        assertThat(source_db.get(k4).get()).isEqualTo(v4);

        // block 2
        map.clear();
        map.put(k2, v3);
        map.put(k3, null);
        db.putBatch(map);

        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isTrue();

        // block 3
        map.clear();
        map.put(k5, v5);
        map.put(k6, v6);
        map.put(k2, null);
        db.putBatch(map);

        assertThat(db.getInsertedKeysCount()).isEqualTo(2);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(4);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isTrue();

        // prune block 0
        db.prune(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // prune block 1
        db.prune(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k4).get()).isEqualTo(v4);
        // not deleted due to block 2 insert
        assertThat(source_db.get(k2).get()).isEqualTo(v3);

        // prune block 2
        db.prune(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isFalse();

        // prune block 3
        db.prune(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(0);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isFalse();
    }

    @Test
    public void pruningTest_woStoredLevel() {
        db.setPruneEnabled(true);

        source_db.put(k2, v2);
        source_db.put(k3, v3);

        // block 2
        db.put(k2, v3);
        db.delete(k3);
        assertThat(db.getInsertedKeysCount()).isEqualTo(1);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isTrue();

        // block 3
        db.put(k5, v5);
        db.put(k6, v6);
        db.delete(k2);
        assertThat(db.getInsertedKeysCount()).isEqualTo(2);
        assertThat(db.getDeletedKeysCount()).isEqualTo(1);
        db.storeBlockChanges(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isTrue();

        // prune block 0 (not stored)
        db.prune(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        // prune block 1 (not stored)
        db.prune(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);

        // prune block 2
        db.prune(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).isPresent()).isFalse();

        // prune block 3
        db.prune(b3, 3);
        assertThat(db.getBlockUpdates().size()).isEqualTo(0);

        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
        assertThat(source_db.get(k2).isPresent()).isFalse();
    }

    @Test
    public void pruningTest_wFork_onCurrentLevel() {
        db.setPruneEnabled(true);

        // block b0
        db.put(k1, v1);
        db.put(k2, v2);
        db.put(k3, v3);
        db.storeBlockChanges(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.keys().size()).isEqualTo(3);
        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // block b1
        db.put(k4, v4);
        db.put(k1, v2);
        db.delete(k2);
        db.storeBlockChanges(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.keys().size()).isEqualTo(4);
        assertThat(source_db.get(k1).get()).isEqualTo(v2);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v4);

        // block b2
        db.put(k5, v5);
        db.delete(k3);
        db.put(k2, v3);
        db.put(k1, v4);
        db.put(k4, v6);
        db.storeBlockChanges(b2, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.keys().size()).isEqualTo(5);
        assertThat(source_db.get(k1).get()).isEqualTo(v4);
        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v6);
        assertThat(source_db.get(k5).get()).isEqualTo(v5);

        // block b3 : note same level as block b2
        db.put(k6, v6);
        db.delete(k4);
        db.put(k2, v4);
        db.put(k1, v3);
        db.storeBlockChanges(b3, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(4);

        assertThat(source_db.keys().size()).isEqualTo(6);
        assertThat(source_db.get(k1).get()).isEqualTo(v3);
        assertThat(source_db.get(k2).get()).isEqualTo(v4);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v6);
        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);

        // prune block b0
        db.prune(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);
        assertThat(source_db.keys().size()).isEqualTo(6);

        // prune block b1
        db.prune(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);
        assertThat(source_db.keys().size()).isEqualTo(6);

        // prune block b3 at level 2 (should be called for main chain block)
        db.prune(b3, 2);
        // also removed the updates for block b2
        assertThat(db.getBlockUpdates().size()).isEqualTo(0);

        assertThat(source_db.keys().size()).isEqualTo(4);
        assertThat(source_db.get(k1).get()).isEqualTo(v3);
        assertThat(source_db.get(k2).get()).isEqualTo(v4);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).isPresent()).isFalse();
        assertThat(source_db.get(k5).isPresent()).isFalse();
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
    }

    @Test
    public void pruningTest_wFork_onPastLevel() {
        db.setPruneEnabled(true);

        // block b0
        db.put(k1, v1);
        db.put(k2, v2);
        db.put(k3, v3);
        db.storeBlockChanges(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);

        assertThat(source_db.keys().size()).isEqualTo(3);
        assertThat(source_db.get(k1).get()).isEqualTo(v1);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);

        // block b1
        db.put(k4, v4);
        db.put(k1, v2);
        db.delete(k2);
        db.storeBlockChanges(b1, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(2);

        assertThat(source_db.keys().size()).isEqualTo(4);
        assertThat(source_db.get(k1).get()).isEqualTo(v2);
        assertThat(source_db.get(k2).get()).isEqualTo(v2);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v4);

        // block b2 : note same level as block b1
        db.put(k5, v5);
        db.delete(k3);
        db.put(k2, v3);
        db.put(k1, v4);
        db.storeBlockChanges(b2, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);

        assertThat(source_db.keys().size()).isEqualTo(5);
        assertThat(source_db.get(k1).get()).isEqualTo(v4);
        assertThat(source_db.get(k2).get()).isEqualTo(v3);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v4);
        assertThat(source_db.get(k5).get()).isEqualTo(v5);

        // block b3
        db.put(k6, v6);
        db.put(k2, v4);
        db.put(k1, v3);
        db.storeBlockChanges(b3, 2);
        assertThat(db.getBlockUpdates().size()).isEqualTo(4);

        assertThat(source_db.keys().size()).isEqualTo(6);
        assertThat(source_db.get(k1).get()).isEqualTo(v3);
        assertThat(source_db.get(k2).get()).isEqualTo(v4);
        assertThat(source_db.get(k3).get()).isEqualTo(v3);
        assertThat(source_db.get(k4).get()).isEqualTo(v4);
        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);

        // prune block b0
        db.prune(b0, 0);
        assertThat(db.getBlockUpdates().size()).isEqualTo(3);
        assertThat(source_db.keys().size()).isEqualTo(6);

        // prune block b2 at level 1 : (should be called for main chain block)
        db.prune(b2, 1);
        assertThat(db.getBlockUpdates().size()).isEqualTo(1);
        assertThat(source_db.keys().size()).isEqualTo(4);
        assertThat(source_db.get(k1).get()).isEqualTo(v3);
        assertThat(source_db.get(k2).get()).isEqualTo(v4);
        assertThat(source_db.get(k3).isPresent()).isFalse();
        assertThat(source_db.get(k4).isPresent()).isFalse();
        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);

        // prune block b3
        db.prune(b3, 2);
        // also removed the updates for block b2
        assertThat(db.getBlockUpdates().size()).isEqualTo(0);

        assertThat(source_db.keys().size()).isEqualTo(4);
        assertThat(source_db.get(k1).get()).isEqualTo(v3);
        assertThat(source_db.get(k2).get()).isEqualTo(v4);
        assertThat(source_db.get(k3).isPresent()).isFalse();
        assertThat(source_db.get(k4).isPresent()).isFalse();
        assertThat(source_db.get(k5).get()).isEqualTo(v5);
        assertThat(source_db.get(k6).get()).isEqualTo(v6);
    }
}
