package org.aion.db.impl.rocksdb;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static org.aion.db.impl.DatabaseFactory.Props;
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
        props.setProperty(Props.DB_TYPE, dbVendor);
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(RocksDBConstants.BLOCK_SIZE));
        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(RocksDBConstants.MAX_OPEN_FILES));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(RocksDBConstants.WRITE_BUFFER_SIZE));

        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNotNull(db);
    }

    // It should return null if given bad vendor
    @Test
    public void testDriverReturnNull() {

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, "BAD VENDOR");
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);

        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNull(db);
    }

    // TODO: parametrize tests with null inputs
    @Test(expected = NullPointerException.class)
    public void testCreateWithNullName() {
        new RocksDBWrapper(null,
                           dbPath,
                           false,
                           false,
                           RocksDBConstants.MAX_OPEN_FILES,
                           RocksDBConstants.BLOCK_SIZE,
                           RocksDBConstants.WRITE_BUFFER_SIZE,
                           RocksDBConstants.READ_BUFFER_SIZE,
                           RocksDBConstants.CACHE_SIZE);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullPath() {
        new RocksDBWrapper(dbName,
                           null,
                           false,
                           false,
                           RocksDBConstants.MAX_OPEN_FILES,
                           RocksDBConstants.BLOCK_SIZE,
                           RocksDBConstants.WRITE_BUFFER_SIZE,
                           RocksDBConstants.READ_BUFFER_SIZE,
                           RocksDBConstants.CACHE_SIZE);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullNameAndPath() {
        new RocksDBWrapper(null,
                           null,
                           false,
                           false,
                           RocksDBConstants.MAX_OPEN_FILES,
                           RocksDBConstants.BLOCK_SIZE,
                           RocksDBConstants.WRITE_BUFFER_SIZE,
                           RocksDBConstants.READ_BUFFER_SIZE,
                           RocksDBConstants.CACHE_SIZE);
    }

}
