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
import static org.aion.mcf.db.DatabaseUtils.deleteRecursively;

import java.io.File;
import java.util.List;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.mcf.db.exception.InvalidFilePathException;
import org.aion.util.TestResources;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

/** @author Alexandra Roatis */
public class PendingBlockStoreTest {

    @Test
    public void testConstructor_wMockDB() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (InvalidFilePathException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();
    }

    @Test
    public void testConstructor_wPersistentDB() {
        File dir = new File(System.getProperty("user.dir"), "tmp");

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        props.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        props.setProperty(Props.DB_NAME, "pbTest");

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (InvalidFilePathException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        assertThat(deleteRecursively(dir)).isTrue();
    }

    @Test
    public void addStatusBlock() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (InvalidFilePathException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<AionBlock> blocks = TestResources.consecutiveBlocks(4);
        assertThat(blocks.size()).isEqualTo(4);

        // test with null input
        assertThat(pb.addStatusBlock(null)).isFalse(); // #index=0 #level=0 #queue=0
        assertThat(pb.getStatusSize()).isEqualTo(0);
        assertThat(pb.getStatusItem(null)).isNull();

        // test with valid block
        AionBlock block = blocks.get(0);
        assertThat(pb.addStatusBlock(block)).isTrue(); // #index=1 #level=1 #queue=1
        assertThat(pb.getStatusSize()).isEqualTo(1);
        assertThat(pb.getStatusItem(block.getHash())).isNotNull();

        // test that block does not get added twice
        assertThat(pb.addStatusBlock(block)).isFalse(); // #index=1 #level=1 #queue=1

        // expand existing queue
        block = blocks.get(1);
        assertThat(pb.addStatusBlock(block)).isTrue(); // #index=2 #level=1 #queue=1
        assertThat(pb.getStatusSize()).isEqualTo(1);
        assertThat(pb.getStatusItem(block.getHash())).isNull();

        // create new queue
        block = blocks.get(3);
        assertThat(pb.addStatusBlock(block)).isTrue(); // #index=3 #level=2 #queue=2
        assertThat(pb.getStatusSize()).isEqualTo(2);
        assertThat(pb.getStatusItem(block.getHash())).isNotNull();

        // expand previous existing queue
        block = blocks.get(2);
        assertThat(pb.addStatusBlock(block)).isTrue(); // #index=4 #level=2 #queue=2
        assertThat(pb.getStatusSize()).isEqualTo(2);
        assertThat(pb.getStatusItem(block.getHash())).isNull();
    }
}
