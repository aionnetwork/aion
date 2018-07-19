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
package org.aion.db.impl.mockdb;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.aion.db.impl.DatabaseFactory.Props.DB_NAME;
import static org.aion.db.impl.DatabaseFactory.Props.DB_TYPE;
import static org.junit.Assert.*;

public class MockDBDriverTest {

    private static IByteArrayKeyValueDatabase db;
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
