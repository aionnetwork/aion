package org.aion.db.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aion.db.impl.rocksdb.RocksDBWrapper;

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
    MOCKDB("mockdb", false),
    /** Using an instance of {@link org.aion.db.impl.mockdb.PersistentMockDB}. */
    PERSISTENTMOCKDB("persistentmockdb", false);

    private static final Map<String, DBVendor> stringToTypeMap = new ConcurrentHashMap<>();

    static {
        for (DBVendor type : DBVendor.values()) {
            stringToTypeMap.put(type.value, type);
        }
    }

    /* map implemented using concurrent hash map */
    private static final List<DBVendor> driverImplementations =
            List.of(LEVELDB, ROCKSDB, H2, MOCKDB);

    private final String value;
    private final boolean persistence;

    DBVendor(final String value, final boolean persistent) {
        this.value = value;
        this.persistence = persistent;
    }

    // public interface
    public static DBVendor fromString(String s) {
        if (s == null) {
            return DBVendor.UNKNOWN;
        }

        DBVendor type = stringToTypeMap.get(s);
        if (type == null) {
            return DBVendor.UNKNOWN;
        }

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

    /** @return {@code false} for a DBVendor with an undefined driver implementation */
    public static boolean hasDriverImplementation(DBVendor v) {
        return driverImplementations.contains(v);
    }
}
