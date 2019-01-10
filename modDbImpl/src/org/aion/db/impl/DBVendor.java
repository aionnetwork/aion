package org.aion.db.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aion.base.db.PersistenceMethod;
import org.aion.db.impl.rocksdb.RocksDBWrapper;

// @ThreadSafe
public enum DBVendor {

    /** Used in correlation with implementations of {@link IDriver}. */
    UNKNOWN("unknown", PersistenceMethod.UNKNOWN), //
    /** Using an instance of {@link org.aion.db.impl.leveldb.LevelDB}. */
    LEVELDB("leveldb", PersistenceMethod.FILE_BASED), //
    /** Using an instance of {@link RocksDBWrapper}. */
    ROCKSDB("rocksdb", PersistenceMethod.FILE_BASED),
    /** Using an instance of {@link org.aion.db.impl.h2.H2MVMap}. */
    H2("h2", PersistenceMethod.FILE_BASED), //
    /** Using an instance of {@Link org.aion.db.impl.mongodb.MongoDB} */
    MONGODB("mongodb", PersistenceMethod.DBMS),
    /** Using an instance of {@link org.aion.db.impl.mockdb.MockDB}. */
    MOCKDB("mockdb", PersistenceMethod.IN_MEMORY),
    /** Using an instance of {@link org.aion.db.impl.mockdb.PersistentMockDB}. */
    PERSISTENTMOCKDB("persistentmockdb", PersistenceMethod.FILE_BASED);

    private static final Map<String, DBVendor> stringToTypeMap = new ConcurrentHashMap<>();

    static {
        for (DBVendor type : DBVendor.values()) {
            stringToTypeMap.put(type.value, type);
        }
    }

    /* map implemented using concurrent hash map */
    private static final List<DBVendor> driverImplementations =
            List.of(LEVELDB, ROCKSDB, H2, MOCKDB, MONGODB);

    private final String value;
    private final PersistenceMethod persistence;

    DBVendor(final String value, final PersistenceMethod persistent) {
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
     * Gets the persistence method of this database vendor
     *
     * @return The persistence method of the database
     */
    public PersistenceMethod getPersistence() {
        return this.persistence;
    }

    /**
     * Gets Whether or not this database uses file-based persistence
     *
     * @return Whether or not this database uses file-based persistence
     */
    public boolean isFileBased() {
        return this.persistence == PersistenceMethod.FILE_BASED;
    }

    /** @return {@code false} for a DBVendor with an undefined driver implementation */
    public static boolean hasDriverImplementation(DBVendor v) {
        return driverImplementations.contains(v);
    }
}
