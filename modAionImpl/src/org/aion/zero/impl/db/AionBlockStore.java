package org.aion.zero.impl.db;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.store.ArrayStore;
import org.aion.db.store.ObjectStore;
import org.aion.db.store.Serializer;
import org.aion.db.store.Stores;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.types.BlockUtil;
import org.slf4j.Logger;

public class AionBlockStore {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    private static final Logger LOG_CONS = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    protected Lock lock = new ReentrantLock();

    private ArrayStore<List<BlockInfo>> index;
    private ObjectStore<Block> blocks;

    private boolean checkIntegrity;

    private Deque<Block> branchingBlk = new ArrayDeque<>(),
            preBranchingBlk = new ArrayDeque<>();
    private long branchingLevel;

    @VisibleForTesting
    public AionBlockStore(ByteArrayKeyValueDatabase index, ByteArrayKeyValueDatabase blocks, boolean checkIntegrity) {
        this(index, blocks, checkIntegrity, 0);
    }

    public AionBlockStore(ByteArrayKeyValueDatabase index, ByteArrayKeyValueDatabase blocks, boolean checkIntegrity, int blockCacheSize) {
        if (index == null) {
            throw new NullPointerException("index db is null");
        }

        if (blocks == null) {
            throw new NullPointerException("block db is null");
        }

        this.index = Stores.newArrayStore(index, BLOCK_INFO_SERIALIZER);

        // Note: because of cache use the blocks db should write lock on get as well
        this.blocks = Stores.newObjectStoreWithCache(blocks, BLOCK_SERIALIZER, blockCacheSize, false);
        this.checkIntegrity = checkIntegrity;
    }

    private static final Serializer<Block> BLOCK_SERIALIZER =
        new Serializer<>() {
            @Override
            public byte[] serialize(Block block) {
                return block.getEncoded();
            }

            @Override
            public Block deserialize(byte[] bytes) {
                Block block = BlockUtil.newBlockFromRlp(bytes);
                if (block != null) {
                    return block;
                } else {
                    throw new NullPointerException("Invalid rlp encode data: " + ByteUtil.toHexString(bytes));
                }
            }
        };

    /**
     *  Get current highest block data, usually use this method when the kernel need to know the
     *  block information itself.
     * @return highest block data stored at the block database
     * @see #getBestBlockWithInfo()
     */
    public Block getBestBlock() {
        lock.lock();

        try {
            long maxLevel = index.size() - 1L;
            if (maxLevel < 0) {
                return null;
            }

            Block bestBlock = getChainBlockByNumber(maxLevel);
            if (bestBlock != null) {
                return bestBlock;
            }

            int depth = 0;
            while (bestBlock == null && depth < 128) {
                --maxLevel;
                bestBlock = getChainBlockByNumber(maxLevel);
                ++depth;
            }

            if (bestBlock == null) {
                LOG.error(
                    "Encountered a kernel database corruption: cannot find blockInfos at level {} in index data store. "
                        + "Or the branch is too deep, it should not happens. "
                        + "Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                    maxLevel,
                    maxLevel - 1);

                throw new IllegalStateException("Index DB corruption or branch too deep.");
            }

            return bestBlock;
        } finally {
            lock.unlock();
        }
    }

    /**
     *  Get current highest block data with extra info relate with the forking rule by given block
     *  hash, usually use this method when the kernel need to know the block data and the forking
     *  information. this method will try to load 2 databases when doing the query. If the kernel
     *  does not need the forking information. Just use #getBestBlock() method.
     * @return highest block data with forking information stored at the block and index database
     * @see #getBestBlock()
     */
    public Block getBestBlockWithInfo() {
        lock.lock();

        try {
            long maxLevel = index.size() - 1L;
            if (maxLevel < 0) {
                return null;
            }

            Block bestBlock = getChainBlockByNumber(maxLevel);

            while (bestBlock == null) {
                --maxLevel;
                bestBlock = getChainBlockByNumber(maxLevel);
            }

            BlockInfo bestBlockInfo = getBlockInfoWithHashAndNumber(bestBlock.getHash(), bestBlock.getNumber());
            if (bestBlockInfo == null) {
                LOG.error(
                    "Encountered a kernel database corruption: cannot find blockInfos at level {} in index data store. "
                        + "Or the branch is too deep, it should not happens. "
                        + "Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                    bestBlock.getNumber(),
                    bestBlock.getNumber() - 1);

                throw new IllegalStateException("The block info of the best block should not be null");
            }

            bestBlock.setTotalDifficulty(bestBlockInfo.getTotalDifficulty());

            return bestBlock;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getBlockHashByNumber(long blockNumber) {
        lock.lock();

        try {
            if (blockNumber < 0L || blockNumber >= index.size()) {
                return null;
            }

            List<BlockInfo> blockInfos = index.get(blockNumber);

            for (BlockInfo blockInfo : blockInfos) {
                if (blockInfo.isMainChain()) {
                    return blockInfo.getHash();
                }
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash) {
        if (branchBlockHash == null || blockNumber < 0L) {
            return null;
        }

        lock.lock();

        try {
            Block branchBlock = blocks.get(branchBlockHash);
            if (branchBlock.getNumber() < blockNumber) {
                throw new IllegalArgumentException(
                    "Requested block number > branch hash number: "
                        + blockNumber
                        + " < "
                        + branchBlock.getNumber());
            }
            while (branchBlock.getNumber() > blockNumber) {
                branchBlock = blocks.get(branchBlock.getParentHash());
            }
            return branchBlock.getHash();
        } finally{
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();

        try {
            blocks.commit();
            index.commit();
        } finally {
            lock.unlock();
        }
    }

    public void saveBlock(Block block, BigInteger totalDifficulty, boolean mainChain) {
        if (block == null) {
            throw new NullPointerException("block is null");
        }

        if (totalDifficulty == null) {
            throw new NullPointerException("Total difficulty is null");
        }

        lock.lock();

        try {
            if (!block.getHeader().isGenesis()) {
                Block parent = getBlockByHashWithInfo(block.getHeader().getParentHash());
                // TODO : [unity] fix the aionblockstore test suite.
                if (parent != null) {
                    if (LOG_CONS.isDebugEnabled()) {
                        LOG_CONS.debug("saveBlock: block {} parent {}", block.toString(), parent.toString());
                    }
                }
            }

            long blockNumber = block.getNumber();
            List<BlockInfo> blockInfos =
                blockNumber >= index.size() ? new ArrayList<>() : index.get(blockNumber);

            // if the blocks are added out of order, the size will be updated without changing the index
            // value
            // useful for concurrency testing and potential parallel sync
            if (blockInfos == null) {
                LOG.error(
                    "Null block information found at " + blockNumber + " when data should exist.");
                blockInfos = new ArrayList<>();
            }

            if (mainChain) {
                for (BlockInfo blockInfo : blockInfos) {
                    blockInfo.setMainChain(false);
                }
            }

            blockInfos.add(new BlockInfo(block.getHash(), totalDifficulty, mainChain));

            blocks.put(block.getHash(), block);
            index.set(block.getNumber(), blockInfos);
        } finally {
            lock.unlock();
        }
    }

    public List<Block> getBlocksByNumber(long number) {
        if (number < 0) {
            return null;
        }

        lock.lock();

        try {
            List<Block> result = new ArrayList<>();

            if (number >= index.size()) {
                return result;
            }

            List<BlockInfo> blockInfos = index.get(number);

            for (BlockInfo blockInfo : blockInfos) {
                byte[] hash = blockInfo.getHash();
                Block block = getBlockByHashWithInfo(hash);
                if (block == null) {
                    throw new NullPointerException("Can find the block in the database!");
                }

                result.add(block);
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    public Block getChainBlockByNumber(long number) {
        lock.lock();

        try {
            long size = index.size();
            if (number < 0L || number >= size) {
                return null;
            }

            List<BlockInfo> blockInfos = index.get(number);

            if (blockInfos == null) {
                LOG.debug(
                    "Can't find the block info at the level {} in the index Database",
                    number);
                return null;
            }

            for (BlockInfo blockInfo : blockInfos) {
                if (blockInfo.isMainChain()) {
                    byte[] hash = blockInfo.getHash();
                    Block block = blocks.get(hash);
                    if (block != null) {
                        block.setTotalDifficulty(blockInfo.getTotalDifficulty());
                        block.setMainChain();
                        return block;
                    }
                    return null;
                }
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote method use for the CLI tooling
     * @param number block number
     * @return list of blocks in the given block level.
     */
    List<Block> getAllChainBlockByNumber(long number) {

        lock.lock();

        try {
            long size = index.size();
            if (number < 0L || number >= size) {
                return null;
            }

            List<BlockInfo> blockInfos = index.get(number);
            if (blockInfos == null) {
                LOG.error(
                    "Encountered a kernel database corruption: cannot find block at level {} in data store.",
                    number);
                LOG.error(
                    " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                    number - 1);
                throw  new IllegalStateException("Index Database corruption! ");
            }

            List<Block> blockList = new ArrayList<>();
            for (BlockInfo blockInfo : blockInfos) {
                Block b = blocks.get(blockInfo.getHash());

                if (b == null) {
                    LOG.error(
                        "Encountered a kernel database corruption: cannot find block with hash {} in data store.",
                        ByteUtil.toHexString(blockInfo.getHash()));
                    LOG.error(
                        " Please shutdown the kernel and re import the database by executing:\t./aion.sh -n <network> --redo-import");
                    throw  new IllegalStateException("blocks Database corruption! ");
                }

                if (blockInfo.isMainChain()) {
                    b.setMainChain();
                }

                b.setTotalDifficulty(blockInfo.getTotalDifficulty());
                blockList.add(b);
            }

            return blockList;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a range of main chain blocks.
     *
     * @param first the height of the first block in the requested range; this block must exist in
     *     the blockchain and be above the genesis to return a non-null output
     * @param last the height of the last block in the requested range; when requesting blocks in
     *     ascending order the last element will be substituted with the best block if its height is
     *     above the best known block
     * @return a list containing consecutive main chain blocks with heights ranging according to the
     *     given parameters; or {@code null} in case of errors or illegal request
     * @apiNote The blocks must be added to the list in the order that they are requested. If {@code
     *     first > last} the blocks are returned in descending order of their height, otherwise when
     *     {@code first < last} the blocks are returned in ascending order of their height.
     */
    public List<Block> getBlocksByRange(long first, long last) {
        if (first <= 0L) {
            return null;
        }

        lock.lock();

        try {
            Block block = getChainBlockByNumber(first);
            if (block == null) {
                // invalid request
                return null;
            }

            if (first == last) {
                return List.of(block);
            } else if (first > last) { // first is highest -> can query directly by parent hash
                List<Block> blocks = new ArrayList<>();
                blocks.add(block);

                for (long i = first - 1; i >= (last > 0 ? last : 1); i--) {
                    block = getBlockByHashWithInfo(block.getParentHash());
                    if (block == null) {
                        // the block should have been stored but null was returned above
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find block at level {} in data store.",
                                i);
                        LOG.error(
                                " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                                i - 1);
                        return null; // stops at any invalid data
                    } else {
                        blocks.add(block);
                    }
                }
                return blocks;
            } else { // last is highest
                LinkedList<Block> blocks = new LinkedList<>();
                Block lastBlock = getChainBlockByNumber(last);

                if (lastBlock == null) { // assuming height was above best block
                    // attempt to get best block
                    lastBlock = getBestBlock();
                    if (lastBlock == null) {
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find best block in data store.");
                        LOG.error(
                                "Please reboot your node to trigger automatic database recovery by the kernel.");
                        return null;
                    } else if (last < lastBlock.getNumber()) {
                        // the block should have been stored but null was returned above
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find block at level {} in data store.",
                                last);
                        LOG.error(
                                " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                                last - 1);
                        return null;
                    }
                }
                // the block was not null
                // or  it was higher than the best block and replaced with the best block

                // building existing range
                blocks.addFirst(lastBlock);
                long newLast = lastBlock.getNumber();
                for (long i = newLast - 1; i > first; i--) {
                    lastBlock = getBlockByHashWithInfo(lastBlock.getParentHash());
                    if (lastBlock == null) {
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find block at level {} in block data store.",
                                i);
                        LOG.error(
                                " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                                i - 1);
                        return null;
                    } else {
                        // always adding at the beginning of the list
                        // to return the expected order of blocks
                        blocks.addFirst(lastBlock);
                    }
                }

                // adding the initial block
                blocks.addFirst(block);
                return blocks;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     *  Get block data by given block hash, usually use this method when the kernel need to know the
     *  block information itself.
     * @param hash the block hash
     * @return block data itself stored at the block database
     * @see #getBlockByHashWithInfo(byte[])
     */
    public Block getBlockByHash(byte[] hash) {
        if (hash == null) {
            return null;
        }

        lock.lock();

        try {
            return blocks.get(hash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Determines if the given block (referenced by hash and number) is already stored in the database.
     *
     * @return {@code true} if the given block exists in the block store, {@code false} otherwise.
     * @implNote The number is used to optimize the search by comparing it to the largest known block height.
     */
    boolean isBlockStored(byte[] hash, long number) {
        if (hash == null) {
            return false;
        }

        lock.lock();

        try {
            // the index size is cached, making this check faster than reading from the db
            if (number >= index.size()) {
                return false;
            } else {
                return blocks.get(hash) != null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieve a block with unity protocol info.
     *
     * @param hash the hash of the requested block
     * @return the block data for the given hash with unity protocol info
     */
    public Block getBlockByHashWithInfo(byte[] hash) {
        if (hash == null) {
            return null;
        }

        lock.lock();

        try {
            return getBlockWithInfo(hash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieve block with unity protocol info for the inner call
     * @param hash given hash of the block
     * @return the block data has matched hash with unity protocol info
     */
    private Block getBlockWithInfo(byte[] hash) {
        Block returnBlock = blocks.get(hash);
        if (returnBlock == null) {
            return null;
        } else {
            BlockInfo blockInfo = getBlockInfoWithHashAndNumber(hash, returnBlock.getNumber());
            if (blockInfo != null) {
                if (blockInfo.isMainChain()) {
                    returnBlock.setMainChain();
                }

                returnBlock.setTotalDifficulty(blockInfo.getTotalDifficulty());
            }
            return returnBlock;
        }
    }

    /**
     * Retrieve the total difficulty given the block hash.
     *
     * @param hash the block hash
     * @return 0 when the hash info is not matched or database corruption. Otherwise, return the
     *     total difficulty info stored in the index database.
     */
    BigInteger getTotalDifficultyForHash(byte[] hash) {
        if (hash == null) {
            return null;
        }

        Block b = getBlockByHashWithInfo(hash);
        if (b == null) {
            return null;
        } else {
            return b.getTotalDifficulty();
        }
    }

    /**
     * Retrieve the block info given the block hash.
     * @param hash the block hash
     * @param blockNumber the blockNumbe relate with the BlockInfoV1
     * @return null when the hash info is not matched or database corruption. Otherwise, return the
     * BlockInfoV1 stored in the index database.
     */
    private BlockInfo getBlockInfoWithHashAndNumber(byte[] hash, long blockNumber) {
        List<BlockInfo> blockInfos = index.get(blockNumber);
        if (blockInfos == null) {
            LOG.error(
                "Encountered a kernel database corruption: cannot find blockInfos at level {} in index data store.",
                blockNumber);
            LOG.error(
                " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                blockNumber - 1);
            return null;
        }

        BlockInfo info = getBlockInfoForHash(blockInfos, hash);
        if (info == null) {
            LOG.error(
                "Encountered a kernel database corruption: cannot find blockInfos at level {} in index data store.",
                blockNumber);
            LOG.error(
                " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                blockNumber - 1);
        }

        return info;
    }

    public long getMaxNumber() {
        lock.lock();

        try {
            return index.size() - 1L;
        } finally {
            lock.unlock();
        }
    }

    public List<byte[]> getListHashesEndWith(byte[] hash, long number) {
        lock.lock();

        try {
            List<Block> blocks = getListBlocksEndWith(hash, number);
            List<byte[]> hashes = new ArrayList<>(blocks.size());

            for (Block b : blocks) {
                hashes.add(b.getHash());
            }

            return hashes;
        } finally {
            lock.unlock();
        }
    }

    public List<BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {
        if (hash == null || qty < 0) {
            return null;
        }

        lock.lock();

        try {
            List<Block> blocks = getListBlocksEndWith(hash, qty);
            List<BlockHeader> headers = new ArrayList<>(blocks.size());

            for (Block b : blocks) {
                headers.add(b.getHeader());
            }

            return headers;
        } finally {
            lock.unlock();
        }
    }

    public List<Block> getListBlocksEndWith(byte[] hash, long qty) {
        if (hash == null || qty < 0) {
            return null;
        }

        Block block = this.blocks.get(hash);

        if (block == null) {
            return new ArrayList<>();
        }

        List<Block> result = new ArrayList<>((int) qty);

        for (int i = 0; i < qty; ++i) {
            result.add(block);
            block = this.blocks.get(block.getParentHash());
            if (block == null) {
                break;
            }
        }

        return result;
    }

    /** @return the common block that was found during the re-branching */
    public long reBranch(Block forkBlock) {
        lock.lock();

        try {
            Block bestBlock = getBestBlock();

            long currentLevel = Math.max(bestBlock.getNumber(), forkBlock.getNumber());

            // 1. First ensure that you are one the save level
            Block forkLine = forkBlock;
            if (forkBlock.getNumber() > bestBlock.getNumber()) {
                branchingLevel = currentLevel;

                while (currentLevel > bestBlock.getNumber()) {
                    List<BlockInfo> blocks = getBlockInfoForLevel(currentLevel);
                    BlockInfo blockInfo = getBlockInfoForHash(blocks, forkLine.getHash());
                    if (blockInfo != null) {
                        blockInfo.setMainChain(true);
                        setBlockInfoForLevel(currentLevel, blocks);

                        // For collecting branching blocks
                        branchingBlk.push(this.blocks.get(blockInfo.getHash()));
                    } else {
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find block with fork line hash {} at the level {} in index data store.",
                                ByteUtil.toHexString(forkLine.getHash()),
                                currentLevel);
                        LOG.error(
                                "Please reboot your node to trigger automatic database recovery by the kernel.");
                    }
                    forkLine = this.blocks.get(forkLine.getParentHash());
                    --currentLevel;
                }
            }

            Block bestLine = bestBlock;
            if (bestBlock.getNumber() > forkBlock.getNumber()) {

                while (currentLevel > forkBlock.getNumber()) {

                    List<BlockInfo> blocks = getBlockInfoForLevel(currentLevel);
                    BlockInfo blockInfo = getBlockInfoForHash(blocks, bestLine.getHash());
                    if (blockInfo != null) {
                        blockInfo.setMainChain(false);
                        setBlockInfoForLevel(currentLevel, blocks);

                        // For collecting prebranching blocks
                        preBranchingBlk.push(this.blocks.get(blockInfo.getHash()));
                    } else {
                        LOG.error(
                                "Encountered a kernel database corruption: cannot find block with best line hash {} at the level {} in index data store.",
                                ByteUtil.toHexString(forkLine.getHash()),
                                currentLevel);
                        LOG.error(
                                "Please reboot your node to trigger automatic database recovery by the kernel.");
                    }
                    bestLine = this.blocks.get(bestLine.getParentHash());
                    --currentLevel;
                }
            }

            // 2. Loop back on each level until common block
            long commonBlockNumber = loopBackToCommonBlock(bestLine, forkLine);

            logBranchingDetails();

            return commonBlockNumber;
        } finally {
            lock.unlock();
        }
    }

    private void logBranchingDetails() {
        if (branchingLevel > 0 && LOG_CONS.isDebugEnabled()) {
            LOG_CONS.debug("Branching details start: level[{}]", branchingLevel);

            LOG_CONS.debug("===== Block details before branch =====");
            while (!preBranchingBlk.isEmpty()) {
                Block blk = preBranchingBlk.pop();
                LOG_CONS.debug("blk: {}", blk.toString());
            }

            LOG_CONS.debug("===== Block details after branch =====");
            while (!branchingBlk.isEmpty()) {
                Block blk = branchingBlk.pop();
                LOG_CONS.debug("blk: {}", blk.toString());
            }

            LOG_CONS.debug("Branching details end");
        }

        // reset branching block details
        branchingLevel = 0;
        branchingBlk.clear();
        preBranchingBlk.clear();
    }

    /**
     * @return the common block that was found during the re-branching
     * @implNote The method calling this method must handle the locking.
     */
    private long loopBackToCommonBlock(Block bestLine, Block forkLine) {
        long currentLevel = bestLine.getNumber();

        if (forkLine.getNumber() != currentLevel) {
            LOG.error("Illegal parameters for loopBackToCommonBlock method.");
            return -1L;
        }

        while (!bestLine.isEqual(forkLine)) {
            List<BlockInfo> levelBlocks = getBlockInfoForLevel(currentLevel);
            BlockInfo bestInfo = getBlockInfoForHash(levelBlocks, bestLine.getHash());
            if (bestInfo != null) {
                bestInfo.setMainChain(false);
                setBlockInfoForLevel(currentLevel, levelBlocks);

                // For collecting preBranching blocks
                preBranchingBlk.push(this.blocks.get(bestInfo.getHash()));
            } else {
                LOG.error(
                        "Encountered a kernel database corruption: cannot find block with best line hash {} at the level {} in index data store.",
                        ByteUtil.toHexString(forkLine.getHash()),
                        currentLevel);
                LOG.error(
                        "Please reboot your node to trigger automatic database recovery by the kernel.");
            }

            BlockInfo forkInfo = getBlockInfoForHash(levelBlocks, forkLine.getHash());
            if (forkInfo != null) {
                forkInfo.setMainChain(true);
                setBlockInfoForLevel(currentLevel, levelBlocks);

                // For collecting branching blocks
                branchingBlk.push(this.blocks.get(forkInfo.getHash()));
            } else {
                LOG.error(
                        "Encountered a kernel database corruption: cannot find block with fork line hash {} at the level {} in index data store.",
                        ByteUtil.toHexString(forkLine.getHash()),
                        currentLevel);
                LOG.error(
                        "Please reboot your node to trigger automatic database recovery by the kernel.");
            }

            bestLine = this.blocks.get(bestLine.getParentHash());
            forkLine = this.blocks.get(forkLine.getParentHash());

            --currentLevel;
        }

        branchingLevel -= currentLevel;

        if (LOG_CONS.isInfoEnabled()) {
            LOG_CONS.info(
                    "branching: common block = {}/{}",
                    forkLine.getNumber(),
                    Hex.toHexString(forkLine.getHash()));
        }

        return currentLevel;
    }

    /**
     * Reverts the blockchain to the given height.
     *
     * @param targetLevel the height of the blockchain that we must revert to
     */
    public void revert(long targetLevel, Logger log) {
        lock.lock();

        try {
            log.info("Block store revert STARTED.");

            Block bestBlock = getBestBlock();
            if (bestBlock == null) {
                log.error("Can't find the best block. Revert FAILED!"
                        + "Please reboot your node to trigger automatic database recovery by the kernel.");
                throw new IllegalStateException("Missing the best block from the database.");
            }

            long currentLevel = bestBlock.getNumber();
            final long TARGET_BATCH_SIZE = 1_000,  TEN_SEC = 10_000_000_000L;
            long currentBatchSize = 0;
            long time = System.nanoTime();

            // ensure that the given level is lower than current
            if (targetLevel >= currentLevel) {
                return;
            }

            // walk back removing blocks greater than the given level value
            while (currentLevel > targetLevel) {
                // remove all the blocks at that level
                List<BlockInfo> currentLevelBlocks = getBlockInfoForLevel(currentLevel);
                if (currentLevelBlocks == null || currentLevelBlocks.isEmpty()) {
                    log.error("Null block information found at " + currentLevel + " when information should exist."
                            + "Please reboot your node to trigger automatic database recovery by the kernel.");
                } else {
                    for (BlockInfo bk_info : currentLevelBlocks) {
                        blocks.deleteInBatch(bk_info.getHash());
                        currentBatchSize++;
                    }
                }

                // remove the level
                index.remove(currentLevel);
                if (currentBatchSize >= TARGET_BATCH_SIZE) {
                    blocks.flushBatch();
                    if (System.nanoTime() - time > TEN_SEC) {
                        log.info("Progress report: current height=" + currentLevel);
                        time = System.nanoTime();
                    }
                    currentBatchSize = 0;
                }
                --currentLevel;
            }
            blocks.flushBatch();

            log.info("Block store revert COMPLETE.");
            log.warn("Please be aware that the current main chain is the same chain that contained the best block encountered at the start of this operation. "
                    + "To keep this revert operation fast the main chain has not been updated based on existing side chains. The main chain will adjust itself when new blocks are imported.");
        } catch (Exception e) {
            // making sure the blocks get deleted if interrupted
            blocks.flushBatch();
        } finally {
            lock.unlock();
        }
    }

    public void pruneAndCorrect() {
        lock.lock();

        try {
            Block block = getBestBlockWithInfo();
            if (block == null) {
                LOG.error("Can't find the best block. PruneAndCorrect failed!");
                LOG.error(
                    "Please reboot your node to trigger automatic database recovery by the kernel.");
                throw new IllegalStateException("Missing the best block from the database.");
            }

            long initialLevel = block.getNumber();
            long level = initialLevel;

            // top down pruning of nodes on side chains
            while (level > 0) {
                pruneSideChains(block);
                block = blocks.get(block.getParentHash());
                if (block == null) {
                    LOG.error(
                            "Block #"
                                    + (level - 1)
                                    + " missing from the database. "
                                    + "Cannot proceed with block pruning and total difficulty updates.");
                    LOG.error(
                            " Please shutdown the kernel and rollback the database by executing:\t./aion.sh -n <network> -r {}",
                            level - 1);
                    throw new IllegalStateException("Missing block from the database.");
                }
                level = block.getNumber();
            }

            // prune genesis
            pruneSideChains(block);

            // bottom up repair of information
            // initial TD set to genesis TD
            level = 1;
            while (level <= initialLevel) {
                BigInteger totalDifficulty = correctTotalDifficulty(level, block.getTotalDifficulty());
                if (totalDifficulty == null) {
                    LOG.error("CorrectTotalDifficulty failed! level:{}", level);
                    throw new IllegalStateException("The Index database might corrupt!");
                }
                LOG.info(
                        "Updated difficulties on level "
                                + level
                                + " to "
                                + " total difficulty: "
                                + totalDifficulty
                                + ".");
                level++;
            }
        } finally {
            lock.unlock();
        }
    }

    /** @implNote The method calling this method must handle the locking. */
    private void pruneSideChains(Block block) {
        lock.lock();

        try {
            // current level
            long level = block.getNumber();
            byte[] blockHash = block.getHash();

            LOG.info("Pruning side chains on level " + level + ".");

            List<BlockInfo> levelBlocks = getBlockInfoForLevel(level);
            BlockInfo blockInfo = getBlockInfoForHash(levelBlocks, blockHash);

            // check if info was there
            while (blockInfo != null) {
                levelBlocks.remove(blockInfo);
                // checking multiple times due to the duplicate info issue
                blockInfo = getBlockInfoForHash(levelBlocks, blockHash);
            }

            // deleting incorrect parallel blocks
            for (BlockInfo wrongBlock : levelBlocks) {
                blocks.delete(wrongBlock.getHash());
            }

            // set new block info with total difficulty set to the block's difficulty
            // This value is corrected in the correctTotalDifficulty() step of pruneAndCorrect()

            blockInfo = new BlockInfo(blockHash, block.getHeader().getDifficultyBI(), true);
            levelBlocks = new ArrayList<>();
            levelBlocks.add(blockInfo);

            setBlockInfoForLevel(level, levelBlocks);
        } finally {
            lock.unlock();
        }
    }

    /** @implNote The method calling this method must handle the locking. */
    private BigInteger correctTotalDifficulty(long level, BigInteger parentTotalDifficulty) {
        List<BlockInfo> levelBlocks = getBlockInfoForLevel(level);

        if (levelBlocks.size() != 1) {
            // something went awry
            LOG.error(
                    "Cannot proceed with total difficulty updates. Previous updates have been overwritten.");
            return null;
        } else {
            // correct block info
            BlockInfo blockInfo = levelBlocks.remove(0);
            // total difficulty previously set to block difficulty
            
            BlockInfo newBlockInfo = new BlockInfo(
                    blockInfo.getHash(),
                    blockInfo.getTotalDifficulty().add(parentTotalDifficulty),
                    blockInfo.isMainChain());
            
            levelBlocks.add(newBlockInfo);
            setBlockInfoForLevel(level, levelBlocks);

            return newBlockInfo.getTotalDifficulty();
        }
    }

    public BigInteger correctIndexEntry(Block block, BigInteger parentTotalDifficulty) {
        if (block == null || parentTotalDifficulty == null) {
            throw new NullPointerException();
        }

        lock.lock();

        try {
            long blockNumber = block.getNumber();
            List<BlockInfo> levelBlocks = getBlockInfoForLevel(blockNumber);
            if (levelBlocks == null) {
                levelBlocks = new ArrayList<>();
            }

            Block parent = getBlockByHashWithInfo(block.getParentHash());
            if (parent == null) {
                LOG.error("The block database corruption");
                LOG.error(
                        " Please shutdown the kernel and re import the database by executing:\t./aion.sh -n <network> --redo-import");
                throw new IllegalStateException("The block database corruption");
            }

            // correct block info
            // assuming side chain, with warnings upon encountered issues
            BlockInfo blockInfo = new BlockInfo(block.getHash(), block.getDifficultyBI().add(parentTotalDifficulty), false);

            // looking through the other block info on that level
            List<BlockInfo> mainChain = new ArrayList<>();
            for (BlockInfo bi : levelBlocks) {
                if (bi.isMainChain()) {
                    mainChain.add(bi);
                }
            }

            // ensuring that there exists only one main chain at present
            if (mainChain.size() > 1) {
                LOG.error(
                        "The database is corrupted. There are two different main chain blocks at level {}."
                                + " Please shutdown the kernel and rollback the block information by executing:\t./aion.sh -r {} -n <network>",
                        blockNumber,
                        blockNumber - 1);
            }

            levelBlocks.add(blockInfo);
            setBlockInfoForLevel(blockNumber, levelBlocks);

            block.setTotalDifficulty(blockInfo.getTotalDifficulty());

            return block.getTotalDifficulty();
        } finally {
            lock.unlock();
        }
    }

    public String dumpPastBlocks(long numberOfBlocks, String reportsFolder) throws IOException {
        lock.lock();

        try {
            long firstBlock = getMaxNumber();
            if (firstBlock < 0) {
                return null;
            }
            long lastBlock = firstBlock - numberOfBlocks;

            File file = new File(reportsFolder, System.currentTimeMillis() + "-blocks-report.out");

            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            while (firstBlock > lastBlock && firstBlock >= 0) {
                List<BlockInfo> levelBlocks = getBlockInfoForLevel(firstBlock);

                writer.append("Blocks at level " + firstBlock + ":");
                writer.newLine();

                for (BlockInfo bi : levelBlocks) {
                    writer.append(
                            "\nBlock hash from index database: "
                                    + Hex.toHexString(bi.getHash())
                                    + "\nTotal Difficulty: "
                                    + bi.getTotalDifficulty()
                                    + "\nBlock on main chain: "
                                    + String.valueOf(bi.isMainChain()).toUpperCase());
                    writer.newLine();
                    Block blk = blocks.get(bi.getHash());
                    if (blk != null) {
                        writer.append("\nFull block data:\n");
                        writer.append(blk.toString());
                        writer.newLine();
                    } else {
                        writer.append("Retrieved block data is null.");
                    }
                }
                writer.newLine();

                firstBlock--;
            }

            writer.close();
            return file.getName();
        } finally {
            lock.unlock();
        }
    }

    String dumpPastBlocksForConsensusTest(long firstBlock, String reportsFolder)
            throws IOException {
        lock.lock();

        try {
            if (firstBlock < 0) {
                return null;
            }

            // the 1st block will be imported by the test
            // its total difficulty, state root and receipt hash can be used for validation
            // the 2nd block will be used to setup the blockchain world state before import
            // the 3rd block is also part of the setup as it is required for header validation
            long lastBlock = firstBlock - 3;

            File file =
                    new File(
                            reportsFolder,
                            System.currentTimeMillis() + "-blocks-for-consensus-test.out");

            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            while (firstBlock > lastBlock && firstBlock >= 0) {
                List<BlockInfo> levelBlocks = getBlockInfoForLevel(firstBlock);

                for (BlockInfo bi : levelBlocks) {
                    if (bi.isMainChain()) {
                        writer.append(
                                "\nBlock hash from index database: "
                                        + Hex.toHexString(bi.getHash())
                                        + "\nTotal Difficulty: "
                                        + bi.getTotalDifficulty()
                                        + "\nBlock on main chain: "
                                        + String.valueOf(bi.isMainChain()).toUpperCase());
                        writer.newLine();
                        Block blk = blocks.get(bi.getHash());
                        if (blk != null) {
                            writer.append("\nFull block data:\n");
                            writer.append(blk.toString());
                            writer.newLine();
                        } else {
                            writer.append("Retrieved block data is null.");
                        }
                    }
                }
                writer.newLine();

                firstBlock--;
            }

            writer.close();
            return file.getName();
        } finally {
            lock.unlock();
        }
    }

    boolean isIndexed(byte[] hash, long level) {
        if (hash == null || level < 0L) {
            return false;
        }

        lock.lock();

        try {
            // when null -> there was no block info for the hash
            return getBlockInfoForHash(getBlockInfoForLevel(level), hash) != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a hash is indexed as main chain or side chain.
     *
     * @param hash the hash for which we check its chain status
     * @param level the height at which the block should be indexed
     * @return {@code true} if the block is indexed as a main chain block, {@code false} if the
     *     block is not indexed or is a side chain block
     */
    public boolean isMainChain(byte[] hash, long level) {
        if (hash == null || level < 0L) {
            return false;
        }

        lock.lock();

        try {
            BlockInfo info = getBlockInfoForHash(getBlockInfoForLevel(level), hash);
            return info != null && info.isMainChain();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a hash is indexed as main chain or side chain; like
     * {@link #isMainChain(byte[], long)}, but less efficient
     *
     * @param hash the hash for which we check its chain status
     *
     * @return {@code true} if the block is indexed as a main chain block, {@code false} if the
     *     block is not indexed or is a side chain block
     */
    public boolean isMainChain(byte[] hash) {
        Block block = getBlockByHash(hash);
        return block == null ? false : isMainChain(hash, block.getNumber());
    }

    /**
     * First checks if the size key is missing or smaller than it should be. If it is incorrect, the
     * method attempts to correct it by setting it to the given level.
     */
    public void correctSize(long maxNumber, Logger log) {
        if (log == null) {
            throw new NullPointerException("logger is null");
        }

        if (maxNumber < 0L) {
            throw new IllegalArgumentException("maxnumber is below then zero");
        }

        lock.lock();

        try {
            // correcting the size if smaller than should be
            long storedSize = index.size();
            if (maxNumber >= storedSize) {
                // can't change size directly, so we do a put + delete the next level to reset it
                index.set(maxNumber + 1, new ArrayList<>());
                index.remove(maxNumber + 1);
                log.info("Corrupted index size corrected from {} to {}.", storedSize, index.size());
            }
        } finally {
            lock.unlock();
        }
    }

    /** Sets the block as main chain and all its ancestors. Used by the data recovery methods. */
    public void correctMainChain(Block block, Logger log) {
        if (block == null) {
            throw new NullPointerException("block is null");
        }

        if (log == null) {
            throw new NullPointerException("log is null");
        }

        lock.lock();

        try {
            Block currentBlock = block;
            List<BlockInfo> infos = getBlockInfoForLevel(currentBlock.getNumber());
            BlockInfo thisBlockInfo = getBlockInfoForHash(infos, currentBlock.getHash());

            // loop stops when the block is null or is already main chain
            while (thisBlockInfo != null && !thisBlockInfo.isMainChain()) {
                log.info(
                        "Setting block hash: {}, number: {} to main chain.",
                        currentBlock.getShortHash(),
                        currentBlock.getNumber());

                // fix the info for the current block
                infos.remove(thisBlockInfo);
                thisBlockInfo.setMainChain(true);
                infos.add(thisBlockInfo);
                setBlockInfoForLevel(currentBlock.getNumber(), infos);

                // fix the info for parent
                currentBlock = blocks.get(currentBlock.getParentHash());
                if (currentBlock != null) {
                    infos = getBlockInfoForLevel(currentBlock.getNumber());
                    thisBlockInfo = getBlockInfoForHash(infos, currentBlock.getHash());
                } else {
                    thisBlockInfo = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performed before --redo-import to clear side chain blocks and reset the index.
     *
     * @param block the block that will be re-imported and should not be removed from the database
     */
     void redoIndexWithoutSideChains(Block block) {
        lock.lock();

        try {

            if (block != null) {
                byte[] currentHash = block.getHash();
                List<BlockInfo> level = getBlockInfoForLevel(block.getNumber());

                // delete all the side-chain blocks
                for (BlockInfo blockInfo : level) {
                    if (!Arrays.equals(currentHash, blockInfo.getHash())) {
                        blocks.delete(blockInfo.getHash());
                    }
                }

                // replace all the block info with empty list
                index.set(block.getNumber(), Collections.emptyList());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves three generation blocks with unity protocol info.
     * <p>
     * Always returns a 3-element array. If the blocks cannot be retrieved the array will contain null values.
     * Block[0] is the parent block and has the given hash. Block[1] is the grandparent block.
     * Block[2] is the great grandparent block.
     *
     * @param hash the hash of the parent block
     * @return the retrieved three generation blocks with unity protocol info
     */
    public final Block[] getThreeGenerationBlocksByHashWithInfo(byte[] hash) {
        Block[] blockFamily = new Block[] { null, null, null};
        if (hash == null) {
            return blockFamily;
        }

        lock.lock();

        try {
            Block block = getBlockWithInfo(hash);
            if (block != null) {
                blockFamily[0] = block;

                Block parentBlock = getBlockWithInfo(block.getParentHash());
                if (parentBlock != null) {
                    blockFamily[1] = parentBlock;

                    Block grandParentBlock = getBlockWithInfo(parentBlock.getParentHash());
                    if (grandParentBlock != null) {
                        blockFamily[2] = grandParentBlock;
                    }
                }
            }

            return blockFamily;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves two generation blocks with unity protocol info.
     * <p>
     * Always returns a 2-element array. If the blocks cannot be retrieved the array will contain null values.
     * Block[0] is the parent block and has the given hash. Block[1] is the grandparent block.
     *
     * @param hash the hash of the parent block
     * @return the retrieved two generation blocks with unity protocol info
     */
    public final Block[] getTwoGenerationBlocksByHashWithInfo(byte[] hash) {
        Block[] blockFamily = new Block[] { null, null};
        if (hash == null) {
            return blockFamily;
        }

        lock.lock();

        try {
            Block block = getBlockWithInfo(hash);
            if (block != null) {
                blockFamily[0] = block;

                Block parentBlock = getBlockWithInfo(block.getParentHash());
                if (parentBlock != null) {
                    blockFamily[1] = parentBlock;
                }
            }

            return blockFamily;
        } finally {
            lock.unlock();
        }
    }

    public static class BlockInfo {

        /**
         * Constructor of the BlockInfo requires 3 arguments
         * input.
         *
         * @param hash block hash
         * @param totalDifficulty the totalDifficulty of this block
         * @param mainChain is belong to mainchain block
         */
        public BlockInfo(byte[] hash, BigInteger totalDifficulty, boolean mainChain) {
            if (hash == null || totalDifficulty == null || totalDifficulty.signum() == -1) {
                throw new IllegalArgumentException();
            }

            this.hash = hash;
            this.totalDifficulty = totalDifficulty;
            this.mainChain = mainChain;
        }

        BlockInfo(RLPList list) {
            this.hash = list.get(0).getRLPData();
            this.totalDifficulty = ByteUtil.bytesToBigInteger(list.get(1).getRLPData());

            byte[] boolData = list.get(2).getRLPData();

            this.mainChain =
                    !(boolData == null || boolData.length == 0) && boolData[0] == (byte) 0x1;
        }
        
        private byte[] hash;

        private BigInteger totalDifficulty;

        private boolean mainChain;

        public byte[] getHash() {
            return hash;
        }

        public void setTotalDifficulty(BigInteger totalDifficulty) {
            this.totalDifficulty = totalDifficulty;
        }

        public BigInteger getTotalDifficulty() {
            return totalDifficulty;
        }

        public void setMainChain(boolean mainChain) {
            this.mainChain = mainChain;
        }

        boolean isMainChain() {
            return mainChain;
        }

        public byte[] getEncoded() {
            byte[] hashElement = RLP.encodeElement(hash);
            byte[] totalDiffElement = RLP.encodeElement(totalDifficulty.toByteArray());
            byte[] mainChainElement = RLP.encodeByte(mainChain ? (byte) 0x1 : (byte) 0x0);
            return RLP.encodeList(hashElement, totalDiffElement, mainChainElement);
        }

        @Override
        public String toString() {
            return "Hash: " + Hex.toHexString(hash) +
                    "\ntotalDifficulty: " + totalDifficulty +
                    "\nisMainChain: " + (mainChain ? "true" : "false");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockInfo blockInfo = (BlockInfo) o;
            return mainChain == blockInfo.mainChain &&
                    Arrays.equals(hash, blockInfo.hash) &&
                    totalDifficulty.equals(blockInfo.totalDifficulty);
        }
    }

    /**
     * Called by {@link AionBlockStore#BLOCK_INFO_SERIALIZER} for now, on main-net launch we should
     * default to this class.
     */
    private static final Serializer<List<BlockInfo>> BLOCK_INFO_RLP_SERIALIZER =
            new Serializer<>() {
                @Override
                public byte[] serialize(List<BlockInfo> object) {
                    byte[][] infoList = new byte[object.size()][];
                    int i = 0;
                    for (BlockInfo b : object) {
                        infoList[i] = b.getEncoded();
                        i++;
                    }
                    return RLP.encodeList(infoList);
                }

                @Override
                public List<BlockInfo> deserialize(byte[] stream) {
                    RLPList list = (RLPList) RLP.decode2(stream).get(0);
                    List<BlockInfo> res = new ArrayList<>(list.size());

                    for (RLPElement aList : list) {

                        RLPList outerList = RLP.decode2(aList.getRLPData());
                        if (outerList.isEmpty()) {
                            throw new IllegalArgumentException(
                                "Rlp decode error during construct the BlockInfo.");
                        }

                        RLPList blockInfoRlp = (RLPList) outerList.get(0);

                        res.add(new BlockInfo(blockInfoRlp));
                    }
                    return res;
                }
            };

    public static final Serializer<List<BlockInfo>> BLOCK_INFO_SERIALIZER =
            new Serializer<>() {

                @Override
                public byte[] serialize(List<BlockInfo> value) {
                    return BLOCK_INFO_RLP_SERIALIZER.serialize(value);
                }

                @SuppressWarnings("unchecked")
                @Override
                public List<BlockInfo> deserialize(byte[] bytes) {
                    if (bytes == null) {
                        throw new NullPointerException();
                    }
                    try {
                        return BLOCK_INFO_RLP_SERIALIZER.deserialize(bytes);
                    } catch (Exception e) {
                        LOG.error("The database object serialer is invalid, please resync your block from scratch");
                        throw new RuntimeException(e);
                    }
                }
            };

    /** @implNote The method calling this method must handle the locking. */
    private List<BlockInfo> getBlockInfoForLevel(long level) {
        // locks acquired by calling method
        return index.get(level);
    }

    /** @implNote The method calling this method must handle the locking. */
    private void setBlockInfoForLevel(long level, List<BlockInfo> infos) {
        // locks acquired by calling method
        index.set(level, infos);
    }

    /**
     * @return the hash information if it is present in the list or {@code null} when the given
     *     block list is {@code null} or the hash is not present in the list
     * @implNote The method calling this method must handle the locking.
     */
    private static BlockInfo getBlockInfoForHash(List<BlockInfo> blocks, byte[] hash) {
        if (blocks != null) {
            for (BlockInfo blockInfo : blocks) {
                if (Arrays.equals(hash, blockInfo.getHash())) {
                    return blockInfo;
                }
            }
        }
        return null;
    }

    public void load() {
        if (checkIntegrity) {
            // the integrity check updates the total difficulty of each block on the chain
            // it uses difficulty addition (not the temporary unity multiplication rule)
            indexIntegrityCheck();
        }
    }

    public enum IntegrityCheckResult {
        ERROR,
        MISSING_GENESIS,
        MISSING_LEVEL,
        FIXED,
        CORRECT
    }

    /**
     *  Don't remove it, see load method and AKI-370
     */
    public IntegrityCheckResult indexIntegrityCheck() {
        lock.lock();

        try {
            if (index.size() > 0) {
                LOG_CONS.info("Checking the integrity of the total difficulty information...");

                // check each block's total difficulty till genesis
                boolean correct = true;
                Block block = getBestBlockWithInfo();
                long start, round, time;
                start = round = System.currentTimeMillis();
                long bestBlockNumber = block.getNumber();

                while (correct && block.getNumber() > 0) {

                    Block parentBlock = getBlockByHashWithInfo(block.getParentHash());
                    if (parentBlock == null) {
                        LOG.error("The database is corrupted. Can not find the parent block {}.", ByteUtil.toHexString(block.getParentHash()));
                        LOG.error(
                            " Please shutdown the kernel and re import the database by executing:\t./aion.sh -n <network> --redo-import");
                        throw new IllegalStateException("The block database corruption");
                    }

                    // it is correct if there is no inconsistency wrt to the parent
                    BigInteger expectedTotalDifficulty = parentBlock.getTotalDifficulty().add(block.getDifficultyBI());

                    correct =
                        getTotalDifficultyForHash(block.getHash()).equals(expectedTotalDifficulty);

                    if (!correct) {
                        LOG_CONS.info(
                                "Total difficulty for block hash: {} number: {} is {}.",
                                block.getShortHash(),
                                block.getNumber(),
                                "NOT OK");
                    } else {
                        time = System.currentTimeMillis();
                        if (time - round > 4999) {
                            long remaining = block.getNumber();
                            long checked = bestBlockNumber - block.getNumber() + 1;
                            double duration = (double)(time - start) / 1000;
                            double approx = remaining * (duration / checked);
                            approx = approx >= 1 ? approx : 1;

                            LOG_CONS.info(
                                    "{} blocks checked in {} sec. {} more blocks to verify. Approximate completion time is {} sec.",
                                    checked,
                                    (long) duration,
                                    remaining,
                                    (long) approx);
                            round = time;
                        }
                    }

                    // check parent next
                    block = parentBlock;
                }

                // check correct TD for genesis block
                if (block.getNumber() == 0) {

                    correct = getTotalDifficultyForHash(block.getHash()).equals(block.getDifficultyBI());

                    if (!correct) {
                        LOG_CONS.info(
                                "Total difficulty for block hash: {} number: {} is {}.",
                                block.getShortHash(),
                                block.getNumber(),
                                "NOT OK");
                    } else {
                        time = ((System.currentTimeMillis() - start) / 1000) + 1;
                        LOG_CONS.info("{} blocks checked in under {} sec.", bestBlockNumber + 1, time);
                    }
                }

                // if any inconsistency, correct the TD
                if (!correct) {
                    LOG_CONS.info(
                            "Integrity check of total difficulty found INVALID information. Correcting ...");

                    List<BlockInfo> infos = getBlockInfoForLevel(0);
                    if (infos == null) {
                        LOG_CONS.error(
                                "Missing genesis block information. Cannot recover without deleting database.");
                        return IntegrityCheckResult.MISSING_GENESIS;
                    }

                    for (BlockInfo bi : infos) {
                        block = blocks.get(bi.getHash());
                        bi = new BlockInfo(block.getHash(), block.getTotalDifficulty(), bi.isMainChain());
                        LOG_CONS.info(
                                "Correcting total difficulty for block hash: {} number: {} to {}.",
                                block.getShortHash(),
                                block.getNumber(),
                                bi.getTotalDifficulty());
                    }
                    setBlockInfoForLevel(0, infos);

                    long level = 1;
                    List<BlockInfo> parentInfos;
                    do {
                        parentInfos = infos;
                        infos = getBlockInfoForLevel(level);
                        if (infos == null) {
                            LOG_CONS.error(
                                    "Missing block information at level {}."
                                            + " Cannot recover without reverting to block number {}.",
                                    level,
                                    (level - 1));
                            return IntegrityCheckResult.MISSING_LEVEL;
                        }

                        for (BlockInfo bi : infos) {
                            block = blocks.get(bi.getHash());
                            BlockInfo parentBlockInfo = getBlockInfoForHash(parentInfos, block.getParentHash());
                            if (parentBlockInfo == null) {
                                LOG_CONS.error(
                                    "Missing block information at level {}."
                                        + " Cannot recover without reverting to block number {}.",
                                    level,
                                    (level - 1));
                                return IntegrityCheckResult.MISSING_LEVEL;
                            }

                            BigInteger newTotalDifficulty = parentBlockInfo.getTotalDifficulty().add(block.getDifficultyBI());
                            
                            LOG_CONS.info(
                                    "Correcting total difficulty for block hash: {} number: {} to {}.",
                                    block.getShortHash(),
                                    block.getNumber(),
                                    newTotalDifficulty);
                        }
                        setBlockInfoForLevel(level, infos);

                        level++;
                    } while (level < index.size());

                    LOG_CONS.info("Total difficulty correction COMPLETE.");
                    return IntegrityCheckResult.FIXED;
                } else {
                    return IntegrityCheckResult.CORRECT;
                }
            } else {
                return IntegrityCheckResult.CORRECT;
            }
        } finally{
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();

        try {
            index.close();
        } catch (Exception e) {
            LOG.error("Not able to close the index database:", e);
        } finally {
            try {
                blocks.close();
            } catch (Exception e) {
                LOG.error("Not able to close the blocks database:", e);
            } finally {
                lock.unlock();
            }
        }
    }

    public void rollback(long blockNumber) {
        if (blockNumber < 0L) {
            throw new IllegalArgumentException();
        }

        lock.lock();

        try {

            long level = index.size() - 1;

            LOG.debug("blockstore rollback block level from {} to {}", level, blockNumber);

            while (level > blockNumber) {
                // remove all the blocks at that level
                List<BlockInfo> currentLevelBlocks = getBlockInfoForLevel(level);

                for (BlockInfo bk_info : currentLevelBlocks) {
                    blocks.delete(bk_info.getHash());
                }

                index.remove(level--);
            }
        } finally {
            lock.unlock();
        }
    }
}
