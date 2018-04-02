package org.aion.db.impl.rocksdb;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RocksDBDriverTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbVendor = DBVendor.ROCKSDB.toValue();
    public static String dbName = "test";

    private DBVendor driver = DBVendor.ROCKSDB;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testDriverReturnDatabase() {

        Properties props = new Properties();
        props.setProperty("db_type", dbVendor);
        props.setProperty("db_name", dbName);
        props.setProperty("db_path", dbPath);
        props.setProperty(DatabaseFactory.PROP_BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(DatabaseFactory.PROP_MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(DatabaseFactory.PROP_WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));

        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNotNull(db);
    }

    // It should return null if given bad vendor
    @Test
    public void testDriverReturnNull() {

        Properties props = new Properties();
        props.setProperty("db_type", "BAD VENDOR");
        props.setProperty("db_name", dbName);
        props.setProperty("db_path", dbPath);

        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNull(db);
    }

    // TODO: parametrize tests with null inputs
    @Test(expected = NullPointerException.class)
    public void testCreateWithNullName() {
        new RocksDBWrapper(null, dbPath, false, false, RocksDBConstants.MAX_OPEN_FILES, RocksDBConstants.BLOCK_SIZE, RocksDBConstants.WRITE_BUFFER_SIZE);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullPath() {
        new RocksDBWrapper(dbName, null, false, false, RocksDBConstants.MAX_OPEN_FILES, RocksDBConstants.BLOCK_SIZE, RocksDBConstants.WRITE_BUFFER_SIZE);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullNameAndPath() {
        new RocksDBWrapper(null, null, false, false, RocksDBConstants.MAX_OPEN_FILES, RocksDBConstants.BLOCK_SIZE, RocksDBConstants.WRITE_BUFFER_SIZE);
    }

}
