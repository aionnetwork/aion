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
import org.aion.db.generic.DatabaseWithCache;
import org.aion.db.generic.LockedDatabase;
import org.aion.db.generic.SpecialLockedDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.MockDBDriver;
import org.aion.db.impl.rocksdb.RocksDBConstants;
import org.aion.db.impl.rocksdb.RocksDBWrapper;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.db.impl.DatabaseFactory.Props;
import static org.junit.Assert.assertNull;

public class DatabaseFactoryTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbName = "test";

    private DBVendor driver = DBVendor.LEVELDB;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testReturnBasicDatabase() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);

        // MOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(MockDB.class.getSimpleName());

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE));
        props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE));

        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LevelDB.class.getSimpleName());

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));

        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(RocksDBWrapper.class.getSimpleName());

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(H2MVMap.class.getSimpleName());

        // MockDBDriver class
        props.setProperty(Props.DB_TYPE, MockDBDriver.class.getName());
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(MockDB.class.getSimpleName());
    }

    @Test
    public void testReturnLockedDatabase() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);
        props.setProperty(Props.ENABLE_LOCKING, "true");

        // MOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(MockDB.class.getSimpleName());

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE));
        props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(SpecialLockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(LevelDB.class.getSimpleName());

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(SpecialLockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(RocksDBWrapper.class.getSimpleName());

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(H2MVMap.class.getSimpleName());

        // DatabaseWithCache class
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        props.setProperty(Props.ENABLE_HEAP_CACHE, "true");
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(DatabaseWithCache.class.getSimpleName());
    }

    @Test
    public void testReturnDatabaseWithCacheParameterSet1() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);
        props.setProperty(Props.ENABLE_LOCKING, "false");
        props.setProperty(Props.ENABLE_HEAP_CACHE, "true");
        props.setProperty(Props.ENABLE_AUTO_COMMIT, "false");
        props.setProperty(Props.MAX_HEAP_CACHE_SIZE, "20");
        props.setProperty(Props.ENABLE_HEAP_CACHE_STATS, "true");

        String autoCmtCheck = "autocommit=OFF";
        String sizeCheck = "size<20";
        String statsCheck = "stats=ON";

        // MOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(MockDB.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE));
        props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(LevelDB.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(RocksDBWrapper.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(H2MVMap.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);
    }

    @Test
    public void testReturnDatabaseWithCacheParameterSet2() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);
        props.setProperty(Props.ENABLE_LOCKING, "false");
        props.setProperty(Props.ENABLE_HEAP_CACHE, "true");
        props.setProperty(Props.ENABLE_AUTO_COMMIT, "true");
        props.setProperty(Props.MAX_HEAP_CACHE_SIZE, "0");
        props.setProperty(Props.ENABLE_HEAP_CACHE_STATS, "false");

        String autoCmtCheck = "autocommit=ON";
        String sizeCheck = "size=UNBOUND";
        String statsCheck = "stats=OFF";

        // MOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(MockDB.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(LevelDBConstants.WRITE_BUFFER_SIZE));
        props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(LevelDBConstants.CACHE_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(LevelDB.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(RocksDBWrapper.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(DatabaseWithCache.class.getSimpleName());
        assertThat(db.toString()).contains(H2MVMap.class.getSimpleName());
        assertThat(db.toString()).contains(autoCmtCheck);
        assertThat(db.toString()).contains(sizeCheck);
        assertThat(db.toString()).contains(statsCheck);
    }

    @Test
    public void testDriverRandomClassReturnNull() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);

        // random class that is not an IDriver
        props.setProperty(Props.DB_TYPE, MockDB.class.getName());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        // System.out.println(db);
        assertNull(db);
    }

    @Test
    public void testDriverRandomStringReturnNull() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);

        // random string
        props.setProperty(Props.DB_TYPE, "not a class");
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        // System.out.println(db);
        assertNull(db);
    }
}
