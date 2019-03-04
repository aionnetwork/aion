package org.aion.db.impl.h2;

import static org.aion.db.impl.DatabaseFactory.Props;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.junit.Test;

public class H2MVMapDriverTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbVendor = DBVendor.H2.toValue();
    public static String dbName = "test";

    private DBVendor driver = DBVendor.H2;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testDriverReturnDatabase() {

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, dbVendor);
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);

        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNotNull(db);
    }

    // It should return null if given bad vendor
    @Test
    public void testDriverReturnNull() {

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, "BAD VENDOR");
        props.setProperty(Props.DB_NAME, dbName);
        props.setProperty(Props.DB_PATH, dbPath);

        ByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        assertNull(db);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullName() {
        new H2MVMap(null, dbPath, false, false);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullPath() {
        new H2MVMap(dbName, null, false, false);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullNameAndPath() {
        new H2MVMap(null, null, false, false);
    }
}
