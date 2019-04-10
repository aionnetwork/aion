package org.aion.db.impl.mockdb;

import static org.aion.db.impl.DatabaseFactory.Props.DB_NAME;
import static org.aion.db.impl.DatabaseFactory.Props.DB_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.junit.Before;
import org.junit.Test;

public class MockDBDriverTest {

    private static ByteArrayKeyValueDatabase db;
    private static DBVendor vendor;
    private static MockDBDriver driver;
    private static Properties props;

    @Before
    public void setUp() {
        vendor = DBVendor.MOCKDB;
        driver = new MockDBDriver();

        props = new Properties();
        props.setProperty(DB_TYPE, vendor.toValue());
        props.setProperty(DB_NAME, "test");

        db = null;
    }

    @Test
    public void testDriverReturnDatabase() {
        // It should return an instance of the HashMapDB given the correct properties.
        db = DatabaseFactory.connect(props);
        assertNotNull(db);

        props.setProperty(DB_TYPE, driver.getClass().getName());
        db = driver.connect(props);
        assertNotNull(db);
    }

    @Test
    public void testDriverReturnNull() {
        // It should return null if given incorrect properties.
        props.setProperty(DB_TYPE, "leveldb");
        db = DatabaseFactory.connect(props);
        assertNull(db);

        db = driver.connect(props);
        assertNull(db);
    }

    @Test
    public void testDriverVersioning() {
        // It should return the proper version of the driver
        int majorVersion = driver.getMajorVersion();
        int minorVersion = driver.getMinorVersion();

        assertEquals(majorVersion, 1);
        assertEquals(minorVersion, 0);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateWithNullName() {
        new MockDB(null);
    }
}
