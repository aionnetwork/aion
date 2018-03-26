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
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.impl.mockdb.MockDBDriver;
import org.junit.Test;

import java.io.File;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DatabaseFactoryTest {

    public static String dbPath = new File(System.getProperty("user.dir"), "tmp").getAbsolutePath();
    public static String dbName = "test";

    private DBVendor driver = DBVendor.LEVELDB;

    // It should return an instance of the DB given the correct properties
    @Test
    public void testDriverReturnDatabase() {
        Properties props = new Properties();
        props.setProperty("db_name", dbName + DatabaseTestUtils.getNext());
        props.setProperty("db_path", dbPath);

        // MOCKDB
        props.setProperty("db_type", DBVendor.MOCKDB.toValue());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        // System.out.println(db.getClass().getName());
        assertNotNull(db);

        // LEVELDB
        props.setProperty("db_type", DBVendor.LEVELDB.toValue());
        props.setProperty(DatabaseFactory.PROP_MAX_FD_ALLOC, String.valueOf(LevelDBConstants.MAX_OPEN_FILES));
        props.setProperty(DatabaseFactory.PROP_BLOCK_SIZE, String.valueOf(LevelDBConstants.BLOCK_SIZE));
        db = DatabaseFactory.connect(props);
        // System.out.println(db.getClass().getName());
        assertNotNull(db);

        // H2
        props.setProperty("db_type", DBVendor.H2.toValue());
        db = DatabaseFactory.connect(props);
        // System.out.println(db.getClass().getName());
        assertNotNull(db);

        // MockDBDriver class
        props.setProperty("db_type", MockDBDriver.class.getName());
        db = DatabaseFactory.connect(props);
        // System.out.println(db.getClass().getName());
        assertNotNull(db);
    }

    @Test
    public void testDriverRandomClassReturnNull() {
        Properties props = new Properties();
        props.setProperty("db_name", dbName + DatabaseTestUtils.getNext());
        props.setProperty("db_path", dbPath);

        // random class that is not an IDriver
        props.setProperty("db_type", MockDB.class.getName());
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        // System.out.println(db);
        assertNull(db);
    }

    @Test
    public void testDriverRandomStringReturnNull() {
        Properties props = new Properties();
        props.setProperty("db_name", dbName + DatabaseTestUtils.getNext());
        props.setProperty("db_path", dbPath);

        // random string
        props.setProperty("db_type", "not a class");
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(props);
        // System.out.println(db);
        assertNull(db);
    }
}
