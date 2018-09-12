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
import java.util.ArrayList;
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
        File dir = new File(System.getProperty("user.dir"), "tmp-" + System.currentTimeMillis());

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

        List<AionBlock> blocks = TestResources.consecutiveBlocks(6);
        assertThat(blocks.size()).isEqualTo(6);

        // test with valid block
        AionBlock block = blocks.remove(0);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=1 #level=1 #queue=1 #status=1
        assertThat(pb.getIndexSize()).isEqualTo(1);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(1);
        assertThat(pb.getStatusItem(block.getHash())).isNotNull();

        // test with valid range
        List<AionBlock> range = new ArrayList<>();
        int rangeSize = 4;
        for (int i = 0; i < rangeSize; i++) {
            range.add(blocks.remove(0));
        }
        assertThat(pb.addBlockRange(range)).isEqualTo(rangeSize);
        // #index=5 #level=2 #queue=2 #status=1
        assertThat(pb.getIndexSize()).isEqualTo(5);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(1);

        // test with valid block expanding range
        block = blocks.remove(0);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=6 #level=2 #queue=2 #status=2
        assertThat(pb.getIndexSize()).isEqualTo(6);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(2);
        assertThat(pb.getStatusItem(block.getHash())).isNull();
        assertThat(pb.getStatusItem(range.get(0).getHash())).isNotNull();

        // flush and close
        pb.flush();
        pb.close();
        assertThat(pb.isOpen()).isFalse();

        // check persistence of storage
        try {
            pb = new PendingBlockStore(props);
        } catch (InvalidFilePathException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        assertThat(pb.getIndexSize()).isEqualTo(6);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(0);

        pb.close();

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
        assertThat(pb.addStatusBlock(null)).isFalse();
        // #index=0 #level=0 #queue=0 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(0);
        assertThat(pb.getLevelSize()).isEqualTo(0);
        assertThat(pb.getQueueSize()).isEqualTo(0);
        assertThat(pb.getStatusSize()).isEqualTo(0);
        assertThat(pb.getStatusItem(null)).isNull();

        // test with valid block
        AionBlock block = blocks.get(0);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=1 #level=1 #queue=1 #status=1
        assertThat(pb.getIndexSize()).isEqualTo(1);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(1);
        assertThat(pb.getStatusItem(block.getHash())).isNotNull();

        // test that block does not get added twice
        assertThat(pb.addStatusBlock(block)).isFalse();
        // #index=1 #level=1 #queue=1 #status=1
        assertThat(pb.getIndexSize()).isEqualTo(1);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(1);

        // expand existing queue
        block = blocks.get(1);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=2 #level=1 #queue=1 #status=1
        assertThat(pb.getIndexSize()).isEqualTo(2);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(1);
        assertThat(pb.getStatusItem(block.getHash())).isNull();

        // create new queue
        block = blocks.get(3);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=3 #level=2 #queue=2 #status=2
        assertThat(pb.getIndexSize()).isEqualTo(3);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(2);
        assertThat(pb.getStatusItem(block.getHash())).isNotNull();

        // expand previous existing queue
        block = blocks.get(2);
        assertThat(pb.addStatusBlock(block)).isTrue();
        // #index=4 #level=2 #queue=2 #status=2
        assertThat(pb.getIndexSize()).isEqualTo(4);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(2);
        assertThat(pb.getStatusItem(block.getHash())).isNull();
    }

    @Test
    public void addBlockRange() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (InvalidFilePathException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<AionBlock> blocks = TestResources.consecutiveBlocks(16);
        assertThat(blocks.size()).isEqualTo(16);

        // test with empty list input
        List<AionBlock> input = new ArrayList<>();
        assertThat(pb.addBlockRange(input)).isEqualTo(0); // #index=0 #level=0 #queue=0
        assertThat(pb.getStatusSize()).isEqualTo(0);

        // test with valid range
        int rangeSize = 4;
        for (int i = 0; i < rangeSize; i++) {
            input.add(blocks.remove(0));
        }
        assertThat(pb.addBlockRange(input)).isEqualTo(rangeSize);
        // #index=4 #level=1 #queue=1 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(0);

        // test that the block range does not get added twice
        assertThat(pb.addBlockRange(input)).isEqualTo(0);
        // #index=4 #level=1 #queue=1 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
        assertThat(pb.getStatusSize()).isEqualTo(0);

        // add new queue though expand existing queue is possible
        // this is done for performance when deleting
        input.clear();
        for (int i = 0; i < rangeSize; i++) {
            input.add(blocks.remove(0));
        }
        assertThat(pb.addBlockRange(input)).isEqualTo(rangeSize);
        // #index=8 #level=2 #queue=2 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize * 2);
        assertThat(pb.getLevelSize()).isEqualTo(2);
        assertThat(pb.getQueueSize()).isEqualTo(2);
        assertThat(pb.getStatusSize()).isEqualTo(0);

        // create new queue
        input.clear();
        for (int i = 0; i < rangeSize; i++) {
            // skips one of the elements
            input.add(blocks.remove(2));
        }
        assertThat(pb.addBlockRange(input)).isEqualTo(rangeSize);
        // #index=12 #level=3 #queue=3 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize * 3);
        assertThat(pb.getLevelSize()).isEqualTo(3);
        assertThat(pb.getQueueSize()).isEqualTo(3);
        assertThat(pb.getStatusSize()).isEqualTo(0);

        // non-consecutive range -> 2 new queues
        input.clear();
        for (int i = 0; i < rangeSize; i++) {
            input.add(blocks.remove(0));
        }
        assertThat(pb.addBlockRange(input)).isEqualTo(rangeSize);
        // #index=16 #level=5 #queue=5 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize * 4);
        assertThat(pb.getLevelSize()).isEqualTo(5);
        assertThat(pb.getQueueSize()).isEqualTo(5);
        assertThat(pb.getStatusSize()).isEqualTo(0);
    }
}
