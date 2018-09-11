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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.db.impl.DatabaseFactory;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.mcf.db.exception.InvalidFilePathException;
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

    /**
     * Ensures that the path defined by the dbFile is valid and generates all the directories that
     * are missing in the path.
     *
     * @param dbFile the path to be verified.
     * @throws InvalidFilePathException when the given path is not valid or the directory structure
     *     cannot be created.
     */
    public static void verifyAndBuildPath(File dbFile) throws InvalidFilePathException {

        // to throw in case of issues with the path
        InvalidFilePathException exception =
                new InvalidFilePathException(
                        "The path «"
                                + dbFile.getAbsolutePath()
                                + "» is not valid as reported by the OS or a read/write permissions error occurred."
                                + " Please provide an alternative DB dbFile path in /config/config.xml.");

        Path path;

        try {
            // ask the OS if the path is valid
            String canonicalPath = dbFile.getCanonicalPath();
            path = Paths.get(canonicalPath);
        } catch (Exception e) {
            throw exception;
        }

        // try to create the directory
        if (!dbFile.exists()) {
            if (!dbFile.mkdirs()) {
                throw exception;
            }
        }

        if (path == null || !Files.isWritable(path) || !Files.isDirectory(path)) {
            throw exception;
        }
    }
}
