package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.db.DatabaseUtils.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.exception.InvalidFileTypeException;
import org.aion.util.TestResources;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.MiningBlockHeader;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.BlockUtil;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class PendingBlockStoreTest {
    @BeforeClass
    public static void setup() {
        // logging to see errors
        AionLoggerFactory.initAll(Map.of(LogEnum.DB, LogLevel.INFO));
    }

    @Test
    public void testConstructor_wMockDB() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();
    }

    @Test(expected = Exception.class)
    public void testConstructor_woVendor() throws Exception {
        Properties props = new Properties();
        new PendingBlockStore(props);
    }

    @Test(expected = Exception.class)
    public void testConstructor_woPathAndName() throws Exception {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.PERSISTENTMOCKDB.toValue());
        new PendingBlockStore(props);
    }

    @Test
    public void testConstructor_wPersistentDB() {
        File dir = new File(System.getProperty("user.dir"), "tmp-" + System.currentTimeMillis());

        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.PERSISTENTMOCKDB.toValue());
        props.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        props.setProperty(Props.DB_NAME, "pbTest");

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<Block> blocks = TestResources.consecutiveBlocks(6);
        assertThat(blocks.size()).isEqualTo(6);

        // test with valid range
        List<Block> range = new ArrayList<>();
        int rangeSize = 4;
        for (int i = 0; i < rangeSize; i++) {
            range.add(blocks.remove(0));
        }
        assertThat(pb.addBlockRange(range)).isEqualTo(rangeSize);
        // #index=4 #level=1 #queue=1
        assertThat(pb.getIndexSize()).isEqualTo(4);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);

        // close
        pb.close();
        assertThat(pb.isOpen()).isFalse();

        // check persistence of storage
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        assertThat(pb.getIndexSize()).isEqualTo(4);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);

        pb.close();

        assertThat(deleteRecursively(dir)).isTrue();
    }

    @Test
    public void testAddBlockRange() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<Block> blocks = TestResources.consecutiveBlocks(16);
        assertThat(blocks.size()).isEqualTo(16);

        // test with empty list input
        List<Block> input = new ArrayList<>();
        assertThat(pb.addBlockRange(input)).isEqualTo(0); // #index=0 #level=0 #queue=0

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

        // test that the block range does not get added twice
        assertThat(pb.addBlockRange(input)).isEqualTo(0);
        // #index=4 #level=1 #queue=1 #status=0
        assertThat(pb.getIndexSize()).isEqualTo(rangeSize);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);

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
    }

    @Test
    public void testAddBlockRange_wException() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<Block> blocks = TestResources.consecutiveBlocks(4);
        assertThat(blocks.size()).isEqualTo(4);

        // closing the pending block store to cause exception
        pb.close();

        assertThat(pb.addBlockRange(blocks)).isEqualTo(0);
    }

    @Test
    public void testLoadBlockRange() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        List<Block> allBlocks = TestResources.consecutiveBlocks(8);

        // 1. test with empty storage
        assertThat(pb.loadBlockRange(100)).isEmpty();

        // 2. test with valid range
        List<Block> blocks = allBlocks.subList(0, 6);
        Block first = blocks.get(0);
        assertThat(blocks.size()).isEqualTo(6);
        assertThat(pb.addBlockRange(blocks)).isEqualTo(6);
        Map<ByteArrayWrapper, List<Block>> actual = pb.loadBlockRange(first.getNumber());
        assertThat(actual.size()).isEqualTo(1);
        assertThat(actual.get(ByteArrayWrapper.wrap(first.getHash()))).isEqualTo(blocks);

        // 3. test with multiple queues

        // create side chain
        AionBlock altBlock = (AionBlock) BlockUtil.newBlockFromRlp(first.getEncoded());
        MiningBlockHeader newHeader = MiningBlockHeader.Builder.newInstance().withHeader(altBlock.getHeader()).withExtraData("random".getBytes()).build();
        altBlock.updateHeader(newHeader);
        assertThat(altBlock.equals(first)).isFalse();
        List<Block> sideChain = new ArrayList<>();
        sideChain.add(altBlock);
        assertThat(pb.addBlockRange(sideChain)).isEqualTo(1);

        // check functionality
        actual = pb.loadBlockRange(first.getNumber());
        assertThat(actual.size()).isEqualTo(2);
        assertThat(actual.get(ByteArrayWrapper.wrap(first.getHash()))).isEqualTo(blocks);
        assertThat(actual.get(ByteArrayWrapper.wrap(altBlock.getHash()))).isEqualTo(sideChain);

        // 4. test with empty level
        long level = first.getNumber();
        assertThat(pb.loadBlockRange(level - 1)).isEmpty();
        assertThat(pb.loadBlockRange(level + 1)).isEmpty();
    }

    @Test
    public void testLoadBlockRange_wException() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        // closing the pending block store to cause exception
        pb.close();

        assertThat(pb.loadBlockRange(100)).isEmpty();
    }

    @Test
    public void testDropPendingQueues() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        // add first queue
        List<Block> blocks = TestResources.consecutiveBlocks(6);
        Block first = blocks.get(0);
        pb.addBlockRange(blocks);

        // add second queue
        AionBlock altBlock = (AionBlock) BlockUtil.newBlockFromRlp(first.getEncoded());
        MiningBlockHeader newHeader = MiningBlockHeader.Builder.newInstance().withHeader(altBlock.getHeader()).withExtraData("random".getBytes()).build();
        altBlock.updateHeader(newHeader);
        List<Block> sideChain = new ArrayList<>();
        sideChain.add(altBlock);
        pb.addBlockRange(sideChain);

        // check storage updates
        assertThat(pb.getIndexSize()).isEqualTo(7);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(2);

        // test drop functionality
        Map<ByteArrayWrapper, List<Block>> actual = pb.loadBlockRange(first.getNumber());
        pb.dropPendingQueues(first.getNumber(), actual.keySet(), actual);

        // check storage after drop functionality
        assertThat(pb.getIndexSize()).isEqualTo(0);
        assertThat(pb.getLevelSize()).isEqualTo(0);
        assertThat(pb.getQueueSize()).isEqualTo(0);
    }

    @Test
    public void testDropPendingQueues_wException() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        // add first queue
        List<Block> blocks = TestResources.consecutiveBlocks(6);
        Block first = blocks.get(0);
        pb.addBlockRange(blocks);

        // add second queue
        AionBlock altBlock = (AionBlock) BlockUtil.newBlockFromRlp(first.getEncoded());
        MiningBlockHeader newHeader = MiningBlockHeader.Builder.newInstance().withHeader(altBlock.getHeader()).withExtraData("random".getBytes()).build();
        altBlock.updateHeader(newHeader);
        List<Block> sideChain = new ArrayList<>();
        sideChain.add(altBlock);
        pb.addBlockRange(sideChain);

        // check storage updates
        assertThat(pb.getIndexSize()).isEqualTo(7);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(2);

        // closing the pending block store to cause exception
        pb.close();

        // test drop functionality
        Map<ByteArrayWrapper, List<Block>> actual = pb.loadBlockRange(first.getNumber());
        pb.dropPendingQueues(first.getNumber(), actual.keySet(), actual);
    }

    @Test
    public void testDropPendingQueues_wSingleQueue() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, DBVendor.MOCKDB.toValue());

        PendingBlockStore pb = null;
        try {
            pb = new PendingBlockStore(props);
        } catch (IOException | InvalidFileTypeException e) {
            e.printStackTrace();
        }
        assertThat(pb.isOpen()).isTrue();

        // add first queue
        List<Block> blocks = TestResources.consecutiveBlocks(6);
        Block first = blocks.get(0);
        pb.addBlockRange(blocks);

        // add second queue
        AionBlock altBlock = (AionBlock) BlockUtil.newBlockFromRlp(first.getEncoded());
        MiningBlockHeader newHeader = MiningBlockHeader.Builder.newInstance().withHeader(altBlock.getHeader()).withExtraData("random".getBytes()).build();
        altBlock.updateHeader(newHeader);
        List<Block> sideChain = new ArrayList<>();
        sideChain.add(altBlock);
        pb.addBlockRange(sideChain);

        // check storage updates
        assertThat(pb.getIndexSize()).isEqualTo(7);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(2);

        // test drop functionality
        Map<ByteArrayWrapper, List<Block>> actual = pb.loadBlockRange(first.getNumber());
        List<ByteArrayWrapper> queues = new ArrayList<>();
        queues.add(ByteArrayWrapper.wrap(first.getHash()));
        pb.dropPendingQueues(first.getNumber(), queues, actual);

        // check storage after drop functionality
        assertThat(pb.getIndexSize()).isEqualTo(1);
        assertThat(pb.getLevelSize()).isEqualTo(1);
        assertThat(pb.getQueueSize()).isEqualTo(1);
    }

    @Test(expected = InvalidFileTypeException.class)
    public void testSwitchDbVendorleveldbToRocksdbException()
        throws InvalidFileTypeException, IOException {

        File dir = new File(System.getProperty("user.dir"), "tmp-" + System.currentTimeMillis());
        Properties levelDB = new Properties();
        levelDB.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        levelDB.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        levelDB.setProperty(Props.DB_NAME, "pbTest");

        PendingBlockStore pb;
        pb = new PendingBlockStore(levelDB);
        assertThat(pb.isOpen()).isTrue();

        List<Block> blocks = TestResources.consecutiveBlocks(16);
        assertThat(blocks.size()).isEqualTo(16);

        assertThat(pb.addBlockRange(blocks)).isEqualTo(16);

        pb.close();

        Properties rocksDB = new Properties();
        rocksDB.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        rocksDB.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        rocksDB.setProperty(Props.DB_NAME, "pbTest");

        try {
            pb = new PendingBlockStore(rocksDB);
        } catch (Exception e) {
            assertThat(deleteRecursively(dir)).isTrue();
            throw e;
        }

        // This test should not reach to here!
        assertThat(pb.isOpen()).isTrue();
        pb.close();
    }

    @Test(expected = InvalidFileTypeException.class)
    public void testSwitchDbVendorRocksdbToLeveldbException()
        throws InvalidFileTypeException, IOException {

        File dir = new File(System.getProperty("user.dir"), "tmp-" + System.currentTimeMillis());
        Properties rocksDB = new Properties();
        rocksDB.setProperty(Props.DB_TYPE, DBVendor.ROCKSDB.toValue());
        rocksDB.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        rocksDB.setProperty(Props.DB_NAME, "pbTest");

        PendingBlockStore pb;
        pb = new PendingBlockStore(rocksDB);
        assertThat(pb.isOpen()).isTrue();

        List<Block> blocks = TestResources.consecutiveBlocks(16);
        assertThat(blocks.size()).isEqualTo(16);

        assertThat(pb.addBlockRange(blocks)).isEqualTo(16);

        pb.close();

        Properties levelDB = new Properties();
        levelDB.setProperty(Props.DB_TYPE, DBVendor.LEVELDB.toValue());
        levelDB.setProperty(Props.DB_PATH, dir.getAbsolutePath());
        levelDB.setProperty(Props.DB_NAME, "pbTest");

        try {
            pb = new PendingBlockStore(levelDB);
        } catch (Exception e) {
            assertThat(deleteRecursively(dir)).isTrue();
            throw e;
        }

        // This test should not reach to here!
        assertThat(pb.isOpen()).isTrue();
        pb.close();
    }
}
