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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    }

    @Test
    public void testDeleteBatch_wPrune() {
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
    }
}
