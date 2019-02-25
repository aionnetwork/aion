package org.aion.base.db;

public enum PersistenceMethod {
    UNKNOWN,

    // The data isn't actually persisted but just stored temporarily in memory
    IN_MEMORY,

    // The data is stored in a file directory
    FILE_BASED,

    // The data is stored in the proprietary format of a database management system
    DBMS
}
