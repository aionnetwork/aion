package org.aion.db.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.db.impl.DatabaseFactory.Props;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Properties;
import org.aion.db.generic.LockedDatabase;
import org.aion.db.generic.SpecialLockedDatabase;
import org.aion.db.impl.h2.H2MVMap;
import org.aion.db.impl.leveldb.LevelDB;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.MockDBDriver;
import org.aion.db.impl.mockdb.PersistentMockDB;
import org.aion.db.impl.rocksdb.RocksDBWrapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseFactoryTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbName = "test";
    public static final Logger log = LoggerFactory.getLogger("DB");

    private DBVendor driver = DBVendor.LEVELDB;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testReturnBasicDatabase() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);

        // MOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());
        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(MockDB.class.getSimpleName());

        // PERSISTENTMOCKDB
        props.setProperty(Props.DB_TYPE, DBVendor.PERSISTENTMOCKDB.toValue());
        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(PersistentMockDB.class.getSimpleName());

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());

        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LevelDB.class.getSimpleName());

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());

        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(RocksDBWrapper.class.getSimpleName());

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(H2MVMap.class.getSimpleName());

        // MockDBDriver class
        props.setProperty(Props.DB_TYPE, MockDBDriver.class.getName());
        db = DatabaseFactory.connect(props, log);
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
        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(MockDB.class.getSimpleName());

        // LEVELDB
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());

        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName())
                .isEqualTo(SpecialLockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(LevelDB.class.getSimpleName());

        // ROCKSDB
        props.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());

        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName())
                .isEqualTo(SpecialLockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(RocksDBWrapper.class.getSimpleName());

        // H2
        props.setProperty(Props.DB_TYPE, DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props, log);
        assertThat(db).isNotNull();
        assertThat(db.getClass().getSimpleName()).isEqualTo(LockedDatabase.class.getSimpleName());
        assertThat(db.toString()).contains(H2MVMap.class.getSimpleName());
    }

    @Test
    public void testDriverRandomClassReturnNull() {
        Properties props = new Properties();
        props.setProperty(Props.DB_NAME, dbName + DatabaseTestUtils.getNext());
        props.setProperty(Props.DB_PATH, dbPath);

        // random class that is not an IDriver
        props.setProperty(Props.DB_TYPE, MockDB.class.getName());
        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
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
        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
        // System.out.println(db);
        assertNull(db);
    }
}
