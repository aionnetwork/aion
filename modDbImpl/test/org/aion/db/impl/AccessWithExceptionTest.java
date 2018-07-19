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
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.utils.FileUtils;
import org.aion.log.AionLoggerFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.db.impl.DatabaseFactory.Props.DB_NAME;

@RunWith(JUnitParamsRunner.class)
public class AccessWithExceptionTest {

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("DB", "TRACE");

        AionLoggerFactory.init(cfg);
    }

    @AfterClass
    public static void teardown() {
        // clean out the tmp directory
        Truth.assertThat(FileUtils.deleteRecursively(DatabaseTestUtils.testDir)).isTrue();
        Truth.assertThat(DatabaseTestUtils.testDir.mkdirs()).isTrue();
    }

    @Before
    public void deleteFromDisk() {
        // clean out the tmp directory
        assertThat(FileUtils.deleteRecursively(DatabaseTestUtils.testDir)).isTrue();
        Truth.assertThat(DatabaseTestUtils.testDir.mkdirs()).isTrue();
    }

    /**
     * @return parameters for testing
     *         {@link #}
     */
    @SuppressWarnings("unused")
    private static Object databaseInstanceDefinitions() {
        return DatabaseTestUtils.databaseInstanceDefinitions();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testIsEmptyWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt isEmpty on closed db
        db.isEmpty();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testKeysWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt keys on closed db
        db.keys();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testGetWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt get on closed db
        db.get(DatabaseTestUtils.randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testPutWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt put on closed db
        db.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testDeleteWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt delete on closed db
        db.delete(DatabaseTestUtils.randomBytes(32));
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testPutBatchWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));
        map.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));
        map.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));

        // attempt putBatch on closed db
        db.putBatch(map);
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testDeleteBatchWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        List<byte[]> list = new ArrayList<>();
        list.add(DatabaseTestUtils.randomBytes(32));
        list.add(DatabaseTestUtils.randomBytes(32));
        list.add(DatabaseTestUtils.randomBytes(32));

        // attempt deleteBatch on closed db
        db.deleteBatch(list);
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testCommitWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // TODO: differentiate between not supported and closed
        // attempt commit on closed db
        db.commit();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testSizeWithClosedDatabase(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.isOpen()).isFalse();

        // attempt approximateSize on closed db
        db.approximateSize();
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testGetWithNullKey(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();

        // attempt get with null key
        db.get(null);
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testPutWithNullKey(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();

        // attempt put with null key
        db.put(null, DatabaseTestUtils.randomBytes(32));
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testDeleteWithNullKey(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();

        // attempt delete with null key
        db.delete(null);
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testPutBatchWithNullKey(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();

        Map<byte[], byte[]> map = new HashMap<>();
        map.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));
        map.put(DatabaseTestUtils.randomBytes(32), DatabaseTestUtils.randomBytes(32));
        map.put(null, DatabaseTestUtils.randomBytes(32));

        // attempt putBatch on closed db
        db.putBatch(map);
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "databaseInstanceDefinitions")
    public void testDeleteBatchWithNullKey(Properties dbDef) {
        // create database
        dbDef.setProperty(DB_NAME, DatabaseTestUtils.dbName + DatabaseTestUtils.getNext());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(dbDef);
        assertThat(db.open()).isTrue();

        List<byte[]> list = new ArrayList<>();
        list.add(DatabaseTestUtils.randomBytes(32));
        list.add(DatabaseTestUtils.randomBytes(32));
        list.add(null);

        // attempt deleteBatch on closed db
        db.deleteBatch(list);
    }
}
