package org.aion.db.impl.leveldb;

import static org.aion.db.impl.DatabaseFactory.Props;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Properties;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDBDriverTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbVendor = DBVendor.LEVELDB.toValue();
    public static String dbName = "test";
    public static final Logger log = LoggerFactory.getLogger("DB");

    private DBVendor driver = DBVendor.LEVELDB;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testDriverReturnDatabase() {

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, dbVendor);
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);

        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
        assertNotNull(db);
    }

    // It should return null if given bad vendor
    @Test
    public void testDriverReturnNull() {

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, "BAD VENDOR");
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);

        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props, log);
        assertNull(db);
    }

    // TODO: parametrize tests with null inputs
    @Test(expected = NullPointerException.class)
    public void testCreateWithNullName() {
        new LevelDB(null, dbPath, log, false, false);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullPath() {
        new LevelDB(dbName, null, log, false, false);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullNameAndPath() {
        new LevelDB(null, null, log, false, false);
    }
}
