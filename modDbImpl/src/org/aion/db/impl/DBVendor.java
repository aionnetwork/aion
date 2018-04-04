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

import org.aion.db.impl.rocksdb.RocksDBWrapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// @ThreadSafe
public enum DBVendor {

    /** Used in correlation with implementations of {@link IDriver}. */
    UNKNOWN("unknown", false), //
    /** Using an instance of {@link org.aion.db.impl.leveldb.LevelDB}. */
    LEVELDB("leveldb", true), //
    /** Using an instance of {@link RocksDBWrapper}. */
    ROCKSDB("rocksdb", true),
    /** Using an instance of {@link org.aion.db.impl.h2.H2MVMap}. */
    H2("h2", true), //
    /** Using an instance of {@link org.aion.db.impl.mockdb.MockDB}. */
    MOCKDB("mockdb", false);

    private static final Map<String, DBVendor> stringToTypeMap = new ConcurrentHashMap<>();

    static {
        for (DBVendor type : DBVendor.values()) {
            stringToTypeMap.put(type.value, type);
        }
    }

    /* map implemented using concurrent hash map */
    private static final List<DBVendor> driverImplementations = List.of(LEVELDB, ROCKSDB, H2, MOCKDB);

    private final String value;
    private final boolean persistence;

    DBVendor(final String value, final boolean persistent) {
        this.value = value;
        this.persistence = persistent;
    }

    // public interface
    public static DBVendor fromString(String s) {
        if (s == null) { return DBVendor.UNKNOWN; }

        DBVendor type = stringToTypeMap.get(s);
        if (type == null) { return DBVendor.UNKNOWN; }

        return type;
    }

    public String toValue() {
        return value;
    }

    /**
     * Check whether the DB provided by the vendor is intended to be persistent.
     *
     * @return {@code true} if the DB provider is intended to be persistent
     */
    public boolean getPersistence() {
        return this.persistence;
    }

    /**
     * @return {@code false} for a DBVendor with an undefined driver implementation
     */
    public static boolean hasDriverImplementation(DBVendor v) {
        return driverImplementations.contains(v);
    }
}
