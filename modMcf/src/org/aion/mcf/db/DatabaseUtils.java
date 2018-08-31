/*
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
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.db;

import java.util.Properties;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DatabaseFactory;
import org.aion.db.impl.DatabaseFactory.Props;
import org.slf4j.Logger;

/** @author Alexandra Roatis */
public class DatabaseUtils {

    public static IByteArrayKeyValueDatabase connectAndOpen(Properties info, Logger LOG) {
        // get the database object
        IByteArrayKeyValueDatabase db = DatabaseFactory.connect(info, LOG.isDebugEnabled());

        // open the database connection
        db.open();

        // check object status
        if (db == null) {
            LOG.error(
                    "Database <{}> connection could not be established for <{}>.",
                    info.getProperty(Props.DB_TYPE),
                    info.getProperty(Props.DB_NAME));
        }

        // check persistence status
        if (!db.isCreatedOnDisk()) {
            LOG.error(
                    "Database <{}> cannot be saved to disk for <{}>.",
                    info.getProperty(Props.DB_TYPE),
                    info.getProperty(Props.DB_NAME));
        }

        return db;
    }
}
