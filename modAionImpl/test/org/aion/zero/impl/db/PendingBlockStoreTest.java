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
package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.db.utils.FileUtils;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.junit.Test;

/** @author Alexandra Roatis */
public class PendingBlockStoreTest {

    @Test
    public void testConstructor_wMockDB() throws InvalidFilePathException {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = new PendingBlockStore(props);
        assertThat(pb.isOpen()).isTrue();
    }

    @Test
    public void testConstructor_wPersistentDB() throws InvalidFilePathException {
        File dir = new File(System.getProperty("user.dir"), "tmp");

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        props.setProperty(Props.DB_NAME, "pbTest");

        PendingBlockStore pb = new PendingBlockStore(props);
        assertThat(pb.isOpen()).isTrue();

        assertThat(FileUtils.deleteRecursively(dir)).isTrue();
    }
}
