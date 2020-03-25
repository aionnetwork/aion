package org.aion.zero.impl.db;

import static org.aion.zero.impl.db.DatabaseUtils.connectAndOpen;
import static org.aion.zero.impl.db.DatabaseUtils.verifyAndBuildPath;
import static org.aion.zero.impl.db.DatabaseUtils.verifyDBfileType;

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory.Props;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Serializer;
import org.aion.db.store.Stores;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.exception.InvalidFileTypeException;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.BlockUtil;
import org.slf4j.Logger;

/**
 * Class for storing blocks that are correct but cannot be imported due to missing parent. Used to
 * speed up lightning sync and backward sync to side chains.
 *
 * <p>The blocks are stored using three data sources:
 *
 * <ul>
 *   <li><b>levels</b>: maps a blockchain height to the queue identifiers that start with blocks at
 *       that height;
 *   <li><b>queues</b>: maps queues identifiers to the list of blocks (in ascending order) that
 *       belong to the queue;
 *   <li><b>indexes</b>: maps block hashes to the identifier of the queue where the block is stored.
 * </ul>
 *
 * Additionally, the class is used to optimize requests for blocks ahead of time by tracking
 * received status blocks and proposing (mostly non-overlapping) base values for the requests.
 *
 * @author Alexandra Roatis
 */
public class PendingBlockStore implements Closeable {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private final ReadWriteLock databaseLock = new ReentrantReadWriteLock();
    private final Lock internalLock = new ReentrantLock();

    // database names
    private static final String LEVEL_DB_NAME = "level";
    private static final String QUEUE_DB_NAME = "queue";
    private static final String INDEX_DB_NAME = "index";

    // data sources: with access managed by the `databaseLock`
    /**
     * Used to map a level (blockchain height) to the queue identifiers that start with blocks at
     * that height.
     */
    private ObjectStore<List<byte[]>> levelSource;

    private ByteArrayKeyValueDatabase levelDatabase;
    /** Used to map a queue identifier to a list of consecutive blocks. */
    private ObjectStore<List<Block>> queueSource;

    private ByteArrayKeyValueDatabase queueDatabase;
    /** Used to maps a block hash to its current queue identifier. */
    private ByteArrayKeyValueDatabase indexSource;

    /**
     * Constructor. Initializes the databases used for storage. If the database configuration used
     * requires persistence, the constructor ensures the path can be accessed or throws an exception
     * if persistence is requested but not achievable.
     *
     * @param _props properties of the databases to be used for storage
     * @throws IllegalStateException when given a persistent database vendor for which the data
     *     store cannot be created or opened.
     */
    public PendingBlockStore(final Properties _props)
        throws IOException, InvalidFileTypeException {
        Properties local = new Properties(_props);

        // check for database persistence requirements
        DBVendor vendor = DBVendor.fromString(local.getProperty(Props.DB_TYPE));
        if (vendor.isFileBased()) {
            File pbFolder =
                    new File(local.getProperty(Props.DB_PATH), local.getProperty(Props.DB_NAME));

            verifyAndBuildPath(pbFolder);

            if (vendor.equals(DBVendor.LEVELDB) || vendor.equals(DBVendor.ROCKSDB)) {
                verifyDBfileType(pbFolder, vendor.toValue());
            }

            local.setProperty(Props.DB_PATH, pbFolder.getAbsolutePath());
        }

        init(local);
    }

    /**
     * Initializes and opens the databases where the pending blocks will be stored.
     *
     * @param props the database properties to be used in initializing the underlying databases
     * @throws IllegalStateException when any of the required databases cannot be instantiated or
     *     opened.
     */
    private void init(Properties props) {
        // create the level source
        props.setProperty(Props.DB_NAME, LEVEL_DB_NAME);
        this.levelDatabase = connectAndOpen(props, LOG);
        if (levelDatabase == null || levelDatabase.isClosed()) {
            throw newException(LEVEL_DB_NAME, props);
        }
        this.levelSource = Stores.newObjectStore(levelDatabase, HASH_LIST_RLP_SERIALIZER);

        // create the queue source
        props.setProperty(Props.DB_NAME, QUEUE_DB_NAME);
        this.queueDatabase = connectAndOpen(props, LOG);
        if (queueDatabase == null || queueDatabase.isClosed()) {
            throw newException(QUEUE_DB_NAME, props);
        }
        this.queueSource = Stores.newObjectStore(queueDatabase, BLOCK_LIST_RLP_SERIALIZER);

        // create the index source
        props.setProperty(Props.DB_NAME, INDEX_DB_NAME);
        this.indexSource = connectAndOpen(props, LOG);
        if (indexSource == null || indexSource.isClosed()) {
            throw newException(INDEX_DB_NAME, props);
        }
    }

    private IllegalStateException newException(String dbName, Properties props) {
        return new IllegalStateException(
                "The «"
                        + dbName
                        + "» database from the pending block store could not be initialized with the given parameters: "
                        + props);
    }

    /**
     * Checks that the underlying storage was correctly initialized and open.
     *
     * @return true if correctly initialized and the databases are open, false otherwise.
     */
    public boolean isOpen() {
        internalLock.lock();
        databaseLock.readLock().lock();

        try {
            return levelSource.isOpen() && queueSource.isOpen() && indexSource.isOpen();
        } finally {
            databaseLock.readLock().unlock();
            internalLock.unlock();
        }
    }

    private static final Serializer<List<byte[]>> HASH_LIST_RLP_SERIALIZER =
            new Serializer<>() {
                @Override
                public byte[] serialize(List<byte[]> object) {
                    byte[][] infoList = new byte[object.size()][];
                    int i = 0;
                    for (byte[] b : object) {
                        infoList[i] = RLP.encodeElement(b);
                        i++;
                    }
                    return RLP.encodeList(infoList);
                }

                @Override
                public List<byte[]> deserialize(byte[] stream) {
                    RLPList list = (RLPList) RLP.decode2(stream).get(0);
                    List<byte[]> res = new ArrayList<>(list.size());

                    for (RLPElement aList : list) {
                        res.add(aList.getRLPData());
                    }
                    return res;
                }
            };

    private static final Serializer<List<Block>> BLOCK_LIST_RLP_SERIALIZER =
            new Serializer<>() {
                @Override
                public byte[] serialize(List<Block> object) {
                    byte[][] infoList = new byte[object.size()][];
                    int i = 0;
                    for (Block b : object) {
                        infoList[i] = b.getEncoded();
                        i++;
                    }
                    return RLP.encodeList(infoList);
                }

                @Override
                public List<Block> deserialize(byte[] stream) {
                    RLPList list = (RLPList) RLP.decode2(stream).get(0);
                    List<Block> res = new ArrayList<>(list.size());

                    for (RLPElement aList : list) {
                        Block block = BlockUtil.newBlockFromRlp(aList.getRLPData());
                        if (block != null) {
                            res.add(block);
                        } else {
                            // logs a NPE to show the stack trace
                            // does not throw the NPE since the program can continue working correctly
                            LOG.warn(
                                    "Unexpected null block retrieved from the pending blocks database for data="
                                            + Arrays.toString(stream),
                                    new NullPointerException());
                        }
                    }
                    return res;
                }
            };

    /**
     * Attempts to store a range of blocks in the pending block store for importing later when the
     * chain reaches the needed height and the parent blocks gets imported.
     *
     * @param blocks a range of blocks that cannot be imported due to height or lack of parent block
     * @return an integer value (ranging from zero to the number of given blocks) representing the
     *     number of blocks that were stored from the given input.
     * @implNote The functionality is optimized for calls providing consecutive blocks, but ensures
     *     correct behavior even when the blocks are not consecutive.
     */
    @VisibleForTesting
    int addBlockRange(List<Block> blocks) {
        return addBlockRange(blocks, LOG);
    }

    /**
     * Attempts to store a range of blocks in the pending block store for importing later when the
     * chain reaches the needed height and the parent blocks gets imported.
     *
     * @param blocks a range of blocks that cannot be imported due to height or lack of parent block
     * @param log external {@link Logger} for displaying messages
     * @return an integer value (ranging from zero to the number of given blocks) representing the
     *     number of blocks that were stored from the given input.
     * @implNote The functionality is optimized for calls providing consecutive blocks, but ensures
     *     correct behavior even when the blocks are not consecutive.
     */
    public int addBlockRange(List<Block> blocks, Logger log) {
        List<Block> blockRange = new ArrayList<>(blocks);

        // nothing to do when 0 blocks given
        if (blockRange.isEmpty()) {
            return 0;
        }

        databaseLock.writeLock().lock();

        try {
            // first block determines the batch queue placement
            Block first = blockRange.remove(0);

            int stored = addBlockRange(first, blockRange);

            // save data to disk
            indexSource.commitBatch();
            levelSource.flushBatch();
            queueSource.flushBatch();

            log.debug("<import-status: STORED {} out of {} blocks, starting with block #{} hash={}>", stored, blocks.size(), first.getNumber(), first.getShortHash());
            // the number of blocks added
            return stored;
        } catch (Exception e) {
            log.error("Unable to store range of blocks due to: ", e);
            return 0;
        } finally {
            databaseLock.writeLock().unlock();
        }
    }

    /**
     * Stores a block ranges with the first element determining the queue placement.
     *
     * @param first the parent block to the lowest block in the given range which will be used to
     *     determine the queue identifier
     * @param blockRange a range of blocks that cannot be imported due to height or lack of parent
     *     block
     * @return an integer value (ranging from zero to the number of given blocks) representing the
     *     number of blocks that were stored from the given input.
     * @implNote Any method calling this functionality must first acquire the needed write lock.
     */
    private int addBlockRange(Block first, List<Block> blockRange) {

        // skip if already stored
        while (indexSource.get(first.getHash()).isPresent()) {
            if (blockRange.isEmpty()) {
                return 0;
            } else {
                first = blockRange.remove(0);
            }
        }

        // the first block is not stored
        // start new queue with hash = first node hash
        byte[] currentQueueHash = first.getHash();
        List<Block> currentQueue = new ArrayList<>();

        // add (to) level
        byte[] levelKey = ByteUtil.longToBytes(first.getNumber());
        List<byte[]> levelData = levelSource.get(levelKey);

        if (levelData == null) {
            levelData = new ArrayList<>();
        }

        levelData.add(currentQueueHash);
        levelSource.putToBatch(levelKey, levelData);

        // index block with queue hash
        indexSource.putToBatch(first.getHash(), currentQueueHash);
        int stored = 1;

        // add element to queue
        currentQueue.add(first);

        // keep track of parent to ensure correct range
        Block parent = first;

        Block current;
        // process rest of block range
        while (!blockRange.isEmpty()) {
            current = blockRange.remove(0);

            // check for issues with batch continuity and storage
            if (!Arrays.equals(current.getParentHash(), parent.getHash()) // continuity issue
                    || indexSource.get(current.getHash()).isPresent()) { // already stored

                // store separately
                stored += addBlockRange(current, blockRange);

                // done with loop
                break;
            }

            // index block to current queue
            indexSource.putToBatch(current.getHash(), currentQueueHash);

            // append block to queue
            currentQueue.add(current);
            stored++;

            // update parent
            parent = current;
        }

        // done with queue
        queueSource.putToBatch(currentQueueHash, currentQueue);

        // the number of blocks added
        return stored;
    }

    /**
     * @return the number of elements stored in the index database.
     * @implNote This method is package private because it is meant to be used for testing.
     */
    @VisibleForTesting
    int getIndexSize() {
        databaseLock.readLock().lock();
        try {
            return countDatabaseKeys(indexSource);
        } finally {
            databaseLock.readLock().unlock();
        }
    }

    /**
     * @return the number of elements stored in the level database.
     * @implNote This method is package private because it is meant to be used for testing.
     */
    @VisibleForTesting
    int getLevelSize() {
        databaseLock.readLock().lock();
        try {
            return countDatabaseKeys(levelDatabase);
        } finally {
            databaseLock.readLock().unlock();
        }
    }

    /**
     * @return the number of elements stored in the queue database.
     * @implNote This method is package private because it is meant to be used for testing.
     */
    @VisibleForTesting
    int getQueueSize() {
        databaseLock.readLock().lock();
        try {
            return countDatabaseKeys(queueDatabase);
        } finally {
            databaseLock.readLock().unlock();
        }
    }

    private static int countDatabaseKeys(ByteArrayKeyValueDatabase db) {
        int size = 0;
        Iterator<byte[]> iterator = db.keys();
        while (iterator.hasNext()) {
            iterator.next();
            size++;
        }
        return size;
    }

    /**
     * Retrieves blocks from storage based on the height of the first block in the range.
     *
     * @param level the height / number of the first block in the queues to be retrieved
     * @return a map of queue identifiers and lists of blocks containing all the separate chain
     *     queues stored at that level.
     */
    @VisibleForTesting
    Map<ByteArrayWrapper, List<Block>> loadBlockRange(long level) {
        return loadBlockRange(level, LOG);
    }

    /**
     * Retrieves blocks from storage based on the height of the first block in the range.
     *
     * @param level the height / number of the first block in the queues to be retrieved
     * @param log the logger used for messages
     * @return a map of queue identifiers and lists of blocks containing all the separate chain
     *     queues stored at that level.
     */
    public Map<ByteArrayWrapper, List<Block>> loadBlockRange(long level, Logger log) {
        databaseLock.readLock().lock();

        try {
            // get the queue for the given level
            List<byte[]> queueHashes = levelSource.get(ByteUtil.longToBytes(level));

            if (queueHashes == null) {
                return Collections.emptyMap();
            }

            // get all the blocks in the given queues
            List<Block> list;
            Map<ByteArrayWrapper, List<Block>> blocks = new HashMap<>();
            for (byte[] queue : queueHashes) {
                list = queueSource.get(queue);
                if (list != null) {
                    ByteArrayWrapper key = ByteArrayWrapper.wrap(queue);
                    blocks.put(key, list);
                    log.debug("Loaded {} blocks from disk from level={} queue={} before filtering.", list.size(), key, level);
                }
            }

            return blocks;
        } catch (Exception e) {
            log.error("Unable to retrieve stored blocks due to: ", e);
            return Collections.emptyMap();
        } finally {
            databaseLock.readLock().unlock();
        }
    }

    /**
     * Used to delete imported queues from storage.
     *
     * @param level the block height of the queue starting point
     * @param queues the identifiers for the queues to be deleted
     * @param blocks the queue to blocks mappings to be deleted (used to ensure that if the queues
     *     have been expanded, only the relevant blocks get deleted)
     */
    @VisibleForTesting
    void dropPendingQueues(long level, Collection<ByteArrayWrapper> queues, Map<ByteArrayWrapper, List<Block>> blocks) {
        dropPendingQueues(level, queues, blocks, LOG);
    }

    /**
     * Used to delete imported queues from storage.
     *
     * @param level the block height of the queue starting point
     * @param queues the identifiers for the queues to be deleted
     * @param blocks the queue to blocks mappings to be deleted (used to ensure that if the queues
     *     have been expanded, only the relevant blocks get deleted)
     * @param log the logger used for messages
     */
    public void dropPendingQueues(long level, Collection<ByteArrayWrapper> queues, Map<ByteArrayWrapper, List<Block>> blocks, Logger log) {

        databaseLock.writeLock().lock();

        try {
            // delete imported queues & blocks
            for (ByteArrayWrapper q : queues) {
                // delete imported blocks
                for (Block b : blocks.get(q)) {
                    // delete index
                    indexSource.deleteInBatch(b.getHash());
                }

                // delete queue
                queueSource.deleteInBatch(q.toBytes());
            }

            // update level
            byte[] levelKey = ByteUtil.longToBytes(level);
            List<byte[]> levelData = levelSource.get(levelKey);

            if (levelData == null) {
                log.error("Corrupt data in PendingBlockStorage. Level (expected to exist) was not found.");
                // level already missing so nothing to do here
            } else {
                List<byte[]> updatedLevelData = new ArrayList<>();

                for (byte[] qHash : levelData) {
                    if (!queues.contains(ByteArrayWrapper.wrap(qHash))) {
                        // this queue was not imported
                        updatedLevelData.add(qHash);
                    }
                }

                if (updatedLevelData.isEmpty()) {
                    // delete level
                    levelSource.deleteInBatch(levelKey);
                } else {
                    // update level
                    levelSource.putToBatch(levelKey, updatedLevelData);
                }
            }

            // push changed to disk
            indexSource.commitBatch();
            queueSource.flushBatch();
            levelSource.flushBatch();

            // log operation
            log.debug("Dropped from storage level = {} with queues = {}.", level, Arrays.toString(queues.toArray()));
        } catch (Exception e) {
            log.error("Unable to delete used blocks due to: ", e);
        } finally {
            databaseLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        databaseLock.writeLock().lock();

        try {
            try {
                levelSource.close();
            } catch (Exception e) {
                LOG.error("Not able to close the pending blocks levels database:", e);
            }

            try {
                queueSource.close();
            } catch (Exception e) {
                LOG.error("Not able to close the pending blocks queue database:", e);
            }

            try {
                indexSource.close();
            } catch (Exception e) {
                LOG.error("Not able to close the pending blocks index database:", e);
            }
        } finally {
            databaseLock.writeLock().unlock();
        }
    }
}
