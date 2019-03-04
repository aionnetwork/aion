package org.aion.db.impl;

import org.aion.interfaces.db.ByteArrayKeyValueDatabase;

public interface IDriver {

    /**
     * Connect to a database. You need to call open() afterward before db operations.
     *
     * @param info the parameters for this database, all represented in String.
     * @return HashMapDB, or null.
     */
    ByteArrayKeyValueDatabase connect(java.util.Properties info);

    /**
     * Retrieves the driver's major version number. Initially this should be 1.
     *
     * @return driver's major version number
     */
    int getMajorVersion();

    /**
     * Gets the driver's minor version number. Initially this should be 0.
     *
     * @return driver's minor version number
     */
    int getMinorVersion();
}
