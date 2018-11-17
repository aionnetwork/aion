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
package org.aion.db.impl.mockdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.aion.base.db.PersistenceMethod;
import org.aion.base.util.ByteArrayWrapper;

/**
 * Provides the same behavior as {@link MockDB} with the addition that data is read from a file on
 * disk at open (if the file exists) and it is stored to disk at close.
 *
 * @author Alexandra Roatis
 */
public class PersistentMockDB extends MockDB {

    public PersistentMockDB(String name, String path) {
        super(name);

        Objects.requireNonNull(path, "The database path cannot be null.");
        this.path = new File(path, name).getAbsolutePath();
    }

    @Override
    public boolean open() {
        if (isOpen()) {
            return true;
        }

        LOG.debug("init database {}", this.toString());

        // using a regular map since synchronization is handled through the read-write lock
        kv = new HashMap<>();

        // load file from disk if it exists
        File dbFile = new File(path);

        if (dbFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(dbFile))) {
                String text;

                while ((text = reader.readLine()) != null) {
                    String[] line = text.split(":", 2);
                    ByteArrayWrapper key = ByteArrayWrapper.wrap(convertToByteArray(line[0]));
                    byte[] value = convertToByteArray(line[1]);
                    kv.put(key, value);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            createFileAndDirectories(dbFile);
        }

        return isOpen();
    }

    private void createFileAndDirectories(File dbFile) {
        try {
            File parent = dbFile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            dbFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final byte[] convertToByteArray(String byteArrayString) {
        String[] numbers = byteArrayString.substring(1, byteArrayString.length() - 1).split(", ");
        byte[] rawData = new byte[numbers.length];
        for (int i = 0; i < rawData.length; i++) {
            rawData[i] = Byte.parseByte(numbers[i]);
        }
        return rawData;
    }

    /**
     * @implNote Persistence is loosely defined here. In this case the data is read from disk at
     *     open and saved to disk at close.
     */
    @Override
    public PersistenceMethod getPersistenceMethod() {
        // Default to file-based since most of our dbs are that
        return PersistenceMethod.FILE_BASED;
    }

    /** @implNote Returns false because data is saved to disk only at close. */
    @Override
    public boolean isCreatedOnDisk() {
        return new File(path).exists();
    }

    @Override
    public void close() {

        // release resources if needed
        if (kv != null) {
            LOG.info("Closing database " + this.toString());

            // save data to disk
            File dbFile = new File(path);

            if (!dbFile.exists()) {
                createFileAndDirectories(dbFile);
            }

            try (FileWriter writer = new FileWriter(dbFile, false)) {
                for (Map.Entry<ByteArrayWrapper, byte[]> entry : kv.entrySet()) {
                    writer.write(
                            Arrays.toString(entry.getKey().getData())
                                    + ":"
                                    + Arrays.toString(entry.getValue())
                                    + "\n");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // clear data
            kv.clear();
        }

        // set map to null
        kv = null;
    }
}
