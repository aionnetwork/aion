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

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.AbstractPowBlockstore;
import org.aion.mcf.ds.DataSourceArray;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.mcf.ds.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.math.BigInteger.ZERO;
import static org.aion.crypto.HashUtil.shortHash;

public class AionBlockStore extends AbstractPowBlockstore<AionBlock, A0BlockHeader> {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());
    private static final Logger LOG_CONS = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    protected ReadWriteLock lock = new ReentrantReadWriteLock();

    private IByteArrayKeyValueDatabase indexDS;
    private DataSourceArray<List<BlockInfo>> index;
    private IByteArrayKeyValueDatabase blocksDS;
    private ObjectDataSource<AionBlock> blocks;

    private boolean checkIntegrity = true;

    public AionBlockStore(IByteArrayKeyValueDatabase index, IByteArrayKeyValueDatabase blocks) {
        init(index, blocks);
    }

    public AionBlockStore(IByteArrayKeyValueDatabase index, IByteArrayKeyValueDatabase blocks, boolean checkIntegrity) {
        this(index, blocks);
        this.checkIntegrity = checkIntegrity;
    }

    private void init(IByteArrayKeyValueDatabase index, IByteArrayKeyValueDatabase blocks) {

        this.indexDS = index;
        this.index = new DataSourceArray<>(new ObjectDataSource<>(index, BLOCK_INFO_SERIALIZER));
        this.blocksDS = blocks;

        this.blocks = new ObjectDataSource<>(blocks, new Serializer<AionBlock, byte[]>() {
            @Override
            public byte[] serialize(AionBlock block) {
                return block.getEncoded();
            }

            @Override
            public AionBlock deserialize(byte[] bytes) {
                return new AionBlock(bytes);
            }
        });

    }

    public AionBlock getBestBlock() {
        lock.readLock().lock();

        try {
            Long maxLevel = getMaxNumber();
            if (maxLevel < 0) {
                return null;
            }

            AionBlock bestBlock = getChainBlockByNumber(maxLevel);
            if (bestBlock != null) {
                return bestBlock;
            }

            while (bestBlock == null) {
                --maxLevel;
                bestBlock = getChainBlockByNumber(maxLevel);
            }

            return bestBlock;
        } finally {
            lock.readLock().unlock();
        }
    }

    public byte[] getBlockHashByNumber(long blockNumber) {
        lock.readLock().lock();

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
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.writeLock().lock();
        try {
            blocks.flush();
            index.flush();
            try {
                if (!blocksDS.isAutoCommitEnabled()) {
                    blocksDS.commit();
                }
            } catch (Exception e) {
                LOG.error("Unable to flush blocks data.", e);
            }
            try {
                if (!indexDS.isAutoCommitEnabled()) {
                    indexDS.commit();
                }
            } catch (Exception e) {
                LOG.error("Unable to flush blocks index data.", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void saveBlock(AionBlock block, BigInteger cummDifficulty, boolean mainChain) {
        lock.writeLock().lock();
        try {
            addInternalBlock(block, cummDifficulty, mainChain);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private void addInternalBlock(AionBlock block, BigInteger cummDifficulty, boolean mainChain) {
        long blockNumber = block.getNumber();
        List<BlockInfo> blockInfos = blockNumber >= index.size() ? new ArrayList<>() : index.get(blockNumber);

        // if the blocks are added out of order, the size will be updated without changing the index value
        // useful for concurrency testing and potential parallel sync
        if (blockInfos == null) {
            LOG.error("Null block information found at " + blockNumber + " when data should exist.");
            blockInfos = new ArrayList<>();
        }

        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setCummDifficulty(cummDifficulty);
        blockInfo.setHash(block.getHash());
        blockInfo.setMainChain(mainChain); // FIXME: maybe here I should force reset main chain for all uncles on that level

        blockInfos.add(blockInfo);

        blocks.put(block.getHash(), block);
        index.set(block.getNumber(), blockInfos);
    }

    public List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> getBlocksByNumber(long number) {
        lock.readLock().lock();

        try {
            List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> result = new ArrayList<>();

            if (number >= index.size()) {
                return result;
            }

            List<BlockInfo> blockInfos = index.get(number);

            for (BlockInfo blockInfo : blockInfos) {

                byte[] hash = blockInfo.getHash();
                AionBlock block = blocks.get(hash);

                result.add(Map.entry(block, Map.entry(blockInfo.getCummDifficulty(), blockInfo.mainChain)));
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public AionBlock getChainBlockByNumber(long number) {
        lock.readLock().lock();

        try {
            long size = index.size();
            if (number < 0L || number >= size) {
                return null;
            }

            List<BlockInfo> blockInfos = index.get(number);

            if (blockInfos == null) {
                return null;
            }

            for (BlockInfo blockInfo : blockInfos) {
                if (blockInfo.isMainChain()) {
                    byte[] hash = blockInfo.getHash();
                    return blocks.get(hash);
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @SuppressWarnings("Duplicates")
    public Map.Entry<AionBlock, BigInteger> getChainBlockByNumberWithTotalDifficulty(long number) {
        lock.readLock().lock();

        try {
            long size = index.size();
            if (number < 0L || number >= size) {
                return null;
            }

            List<BlockInfo> blockInfos = index.get(number);

            for (BlockInfo blockInfo : blockInfos) {
                if (blockInfo.isMainChain()) {
                    byte[] hash = blockInfo.getHash();
                    return Map.entry(blocks.get(hash), blockInfo.getCummDifficulty());
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public AionBlock getBlockByHash(byte[] hash) {
        lock.readLock().lock();
        try {
            return blocks.get(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isBlockExist(byte[] hash) {
        return getBlockByHash(hash) != null;
    }

    @Override
    public BigInteger getTotalDifficultyForHash(byte[] hash) {
        lock.readLock().lock();

        try {
            IAionBlock block = this.getBlockByHash(hash);
            if (block == null) {
                return ZERO;
            }

            Long level = block.getNumber();
            List<BlockInfo> blockInfos = index.get(level.longValue());
            if (blockInfos == null){
                return ZERO;
            }
            for (BlockInfo blockInfo : blockInfos) {
                if (Arrays.equals(blockInfo.getHash(), hash)) {
                    return blockInfo.getCummDifficulty();
                }
            }

            return ZERO;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public BigInteger getTotalDifficulty() {
        lock.readLock().lock();

        try {
            long maxNumber = getMaxNumber();

            List<BlockInfo> blockInfos = index.get(maxNumber);
            for (BlockInfo blockInfo : blockInfos) {
                if (blockInfo.isMainChain()) {
                    return blockInfo.getCummDifficulty();
                }
            }

            while (true) {
                --maxNumber;
                List<BlockInfo> infos = getBlockInfoForLevel(maxNumber);

                for (BlockInfo blockInfo : infos) {
                    if (blockInfo.isMainChain()) {
                        return blockInfo.getCummDifficulty();
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getMaxNumber() {
        lock.readLock().lock();

        try {
            // the index size is always >= 0
            return index.size() - 1L;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<byte[]> getListHashesEndWith(byte[] hash, long number) {
        lock.readLock().lock();

        try {
            List<AionBlock> blocks = getListBlocksEndWith(hash, number);
            List<byte[]> hashes = new ArrayList<>(blocks.size());

            for (IAionBlock b : blocks) {
                hashes.add(b.getHash());
            }

            return hashes;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<A0BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {
        lock.readLock().lock();
        try {
            List<AionBlock> blocks = getListBlocksEndWith(hash, qty);
            List<A0BlockHeader> headers = new ArrayList<>(blocks.size());

            for (IAionBlock b : blocks) {
                headers.add(b.getHeader());
            }

            return headers;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<AionBlock> getListBlocksEndWith(byte[] hash, long qty) {
        lock.readLock().lock();
        try {
            return getListBlocksEndWithInner(hash, qty);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private List<AionBlock> getListBlocksEndWithInner(byte[] hash, long qty) {
        // locks acquired by calling method
        AionBlock block = this.blocks.get(hash);

        if (block == null) {
            return new ArrayList<>();
        }

        List<AionBlock> blocks = new ArrayList<>((int) qty);

        for (int i = 0; i < qty; ++i) {
            blocks.add(block);
            block = this.blocks.get(block.getParentHash());
            if (block == null) {
                break;
            }
        }

        return blocks;
    }

    @Override
    public void reBranch(AionBlock forkBlock) {
        lock.writeLock().lock();

        try {
            IAionBlock bestBlock = getBestBlock();

            long currentLevel = Math.max(bestBlock.getNumber(), forkBlock.getNumber());

            // 1. First ensure that you are one the save level
            IAionBlock forkLine = forkBlock;
            if (forkBlock.getNumber() > bestBlock.getNumber()) {

                while (currentLevel > bestBlock.getNumber()) {
                    List<BlockInfo> blocks = getBlockInfoForLevel(currentLevel);
                    BlockInfo blockInfo = getBlockInfoForHash(blocks, forkLine.getHash());
                    if (blockInfo != null) {
                        blockInfo.setMainChain(true);
                        setBlockInfoForLevel(currentLevel, blocks);
                    } else {
                        LOG.error("Null block information found at " + currentLevel + " when data should exist.");
                    }
                    forkLine = getBlockByHash(forkLine.getParentHash());
                    --currentLevel;
                }
            }

            IAionBlock bestLine = bestBlock;
            if (bestBlock.getNumber() > forkBlock.getNumber()) {

                while (currentLevel > forkBlock.getNumber()) {

                    List<BlockInfo> blocks = getBlockInfoForLevel(currentLevel);
                    BlockInfo blockInfo = getBlockInfoForHash(blocks, bestLine.getHash());
                    if (blockInfo != null) {
                        blockInfo.setMainChain(false);
                        setBlockInfoForLevel(currentLevel, blocks);
                    } else {
                        LOG.error("Null block information found at " + currentLevel + " when data should exist.");
                    }
                    bestLine = getBlockByHash(bestLine.getParentHash());
                    --currentLevel;
                }
            }

            // 2. Loop back on each level until common block
            loopBackToCommonBlock(bestLine, forkLine);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private void loopBackToCommonBlock(IAionBlock bestLine, IAionBlock forkLine) {
        long currentLevel = bestLine.getNumber();

        if (forkLine.getNumber() != currentLevel) {
            LOG.error("Illegal parameters for loopBackToCommonBlock method.");
            return;
        }

        while (!bestLine.isEqual(forkLine)) {

            List<BlockInfo> levelBlocks = getBlockInfoForLevel(currentLevel);
            BlockInfo bestInfo = getBlockInfoForHash(levelBlocks, bestLine.getHash());
            if (bestInfo != null) {
                bestInfo.setMainChain(false);
                setBlockInfoForLevel(currentLevel, levelBlocks);
            } else {
                LOG.error("Null block information found at " + currentLevel + " when information should exist.");
            }

            BlockInfo forkInfo = getBlockInfoForHash(levelBlocks, forkLine.getHash());
            if (forkInfo != null) {
                forkInfo.setMainChain(true);
                setBlockInfoForLevel(currentLevel, levelBlocks);
            } else {
                LOG.error("Null block information found at " + currentLevel + " when information should exist.");
            }

            bestLine = getBlockByHash(bestLine.getParentHash());
            forkLine = getBlockByHash(forkLine.getParentHash());

            --currentLevel;
        }

        AionLoggerFactory.getLogger(LogEnum.CONS.name())
                .info("branching: common block = {}/{}", forkLine.getNumber(), Hex.toHexString(forkLine.getHash()));
    }

    @Override
    public void revert(long previousLevel) {
        lock.writeLock().lock();

        try {
            IAionBlock bestBlock = getBestBlock();

            long currentLevel = bestBlock.getNumber();

            // ensure that the given level is lower than current
            if (previousLevel >= currentLevel) {
                return;
            }

            // walk back removing blocks greater than the given level value
            IAionBlock bestLine = bestBlock;
            while (currentLevel > previousLevel) {

                // remove all the blocks at that level
                List<BlockInfo> currentLevelBlocks = getBlockInfoForLevel(currentLevel);
                if (currentLevelBlocks == null || currentLevelBlocks.size() == 0) {
                    blocks.delete(bestLine.getHash());
                    LOG.error("Null block information found at " + currentLevel + " when information should exist.");
                } else {
                    for (BlockInfo bk_info : currentLevelBlocks) {
                        blocks.delete(bk_info.getHash());
                    }
                }

                // remove the level
                index.remove(currentLevel);
                if (bestLine != null) {
                    bestLine = getBlockByHash(bestLine.getParentHash());
                } else {
                    // attempt to find another block at the parent level
                    bestLine = getChainBlockByNumber(currentLevel - 1);
                }
                --currentLevel;
            }

            if (bestLine == null) {
                LOG.error("Block at level #" + previousLevel + " is null. Reverting further back may be required.");
            } else {
                // update the main chain based on difficulty, if needed
                List<BlockInfo> blocks = getBlockInfoForLevel(previousLevel);
                BlockInfo blockInfo = getBlockInfoForHash(blocks, bestLine.getHash());

                // no side chains at this level
                if (blocks.size() == 1 && blockInfo != null) {
                    if (!blockInfo.isMainChain()) {
                        blockInfo.setMainChain(true);
                        setBlockInfoForLevel(previousLevel, blocks);
                    }
                } else {
                    if (blockInfo == null) {
                        LOG.error("Null block information found at " + previousLevel + " when data should exist. "
                                + "Rebuilding information.");

                        // recreate missing block info
                        blockInfo = new BlockInfo();
                        blockInfo.setCummDifficulty(getTotalDifficultyForHash(bestLine.getParentHash())
                                .add(bestLine.getHeader().getDifficultyBI()));
                        blockInfo.setHash(bestLine.getHash());
                        blocks.add(blockInfo);
                    }

                    // check for max total difficulty
                    BlockInfo maxTDInfo = blockInfo;
                    for (BlockInfo info : blocks) {
                        if (info.getCummDifficulty().compareTo(maxTDInfo.getCummDifficulty()) > 0) {
                            maxTDInfo = info;
                        }
                    }

                    // 2. Loop back on each level until common block
                    IAionBlock forkLine = getBlockByHash(maxTDInfo.getHash());
                    loopBackToCommonBlock(bestLine, forkLine);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void pruneAndCorrect() {
        lock.writeLock().lock();

        try {
            IAionBlock block = getBestBlock();
            long initialLevel = block.getNumber();
            long level = initialLevel;

            // top down pruning of nodes on side chains
            while (level > 0) {
                pruneSideChains(block);
                block = getBlockByHash(block.getParentHash());
                if (block == null) {
                    LOG.error("Block #" + (level - 1) + " missing from the database. "
                            + "Cannot proceed with block pruning and total difficulty updates.");
                    return;
                }
                level = block.getNumber();
            }

            // prune genesis
            pruneSideChains(block);

            // bottom up repair of information
            // initial TD set to genesis TD
            BigInteger parentTotalDifficulty = block.getHeader().getDifficultyBI();
            level = 1;
            while (level <= initialLevel) {
                parentTotalDifficulty = correctTotalDifficulty(level, parentTotalDifficulty);
                LOG.info("Updated total difficulty on level " + level + " to " + parentTotalDifficulty + ".");
                level++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private void pruneSideChains(IAionBlock block) {
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

        // set new block info with total difficulty = block difficulty
        blockInfo = new BlockInfo();
        blockInfo.setCummDifficulty(block.getHeader().getDifficultyBI());
        blockInfo.setHash(blockHash);
        blockInfo.setMainChain(true);

        levelBlocks = new ArrayList<>();
        levelBlocks.add(blockInfo);

        setBlockInfoForLevel(level, levelBlocks);
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private BigInteger correctTotalDifficulty(long level, BigInteger parentTotalDifficulty) {
        List<BlockInfo> levelBlocks = getBlockInfoForLevel(level);

        if (levelBlocks.size() != 1) {
            // something went awry
            LOG.error("Cannot proceed with total difficulty updates. Previous updates have been overwritten.");
            return null;
        } else {
            // correct block info
            BlockInfo blockInfo = levelBlocks.remove(0);
            // total difficulty previously set to block difficulty
            blockInfo.setCummDifficulty(blockInfo.getCummDifficulty().add(parentTotalDifficulty));
            levelBlocks.add(blockInfo);
            setBlockInfoForLevel(level, levelBlocks);

            return blockInfo.getCummDifficulty();
        }
    }

    public BigInteger correctIndexEntry(AionBlock block, BigInteger parentTotalDifficulty) {
        lock.writeLock().lock();

        try {
            long blockNumber = block.getNumber();
            List<BlockInfo> levelBlocks = getBlockInfoForLevel(blockNumber);
            if (levelBlocks == null) {
                levelBlocks = new ArrayList<>();
            }

            // correct block info
            BlockInfo blockInfo = getBlockInfoForHash(levelBlocks, block.getHash());
            if (blockInfo == null) {
                blockInfo = new BlockInfo();
            }
            blockInfo.setHash(block.getHash());
            blockInfo.setCummDifficulty(block.getDifficultyBI().add(parentTotalDifficulty));
            // assuming side chain, with warnings upon encountered issues
            blockInfo.setMainChain(false);

            // looking through the other block info on that level
            List<BlockInfo> mainChain = new ArrayList<>();
            for (BlockInfo bi : levelBlocks) {
                if (bi.isMainChain()) {
                    mainChain.add(bi);
                }
            }

            // ensuring that there exists only one main chain at present
            if (mainChain.size() > 1) {
                LOG.error("The database is corrupted. There are two different main chain blocks at level {}."
                                  + " Please stop the kernel and repair the block information by executing:\t./aion.sh -r",
                          blockNumber);
            }

            levelBlocks.add(blockInfo);
            setBlockInfoForLevel(blockNumber, levelBlocks);

            return blockInfo.getCummDifficulty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String dumpPastBlocks(long numberOfBlocks, String reportsFolder) throws IOException {
        lock.readLock().lock();

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
                    writer.append("\nBlock hash from index database: " + Hex.toHexString(bi.getHash())
                                          + "\nTotal Difficulty: " + bi.getCummDifficulty() + "\nBlock on main chain: "
                                          + String.valueOf(bi.isMainChain()).toUpperCase());
                    writer.newLine();
                    AionBlock blk = getBlockByHash(bi.getHash());
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
            lock.readLock().unlock();
        }
    }

    public List<byte[]> getListHashesStartWith(long number, long maxBlocks) {
        lock.readLock().lock();

        try {
            List<byte[]> result = new ArrayList<>();

            int i;
            for (i = 0; i < maxBlocks; ++i) {
                List<BlockInfo> blockInfos = index.get(number);
                if (blockInfos == null) {
                    break;
                }

                for (BlockInfo blockInfo : blockInfos) {
                    if (blockInfo.isMainChain()) {
                        result.add(blockInfo.getHash());
                        break;
                    }
                }

                ++number;
            }
            maxBlocks -= i;

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isIndexed(byte[] hash, long level) {
        lock.readLock().lock();

        try {
            // when null -> there was no block info for the hash
            return getBlockInfoForHash(getBlockInfoForLevel(level), hash) != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * First checks if the size key is missing or smaller than it should be.
     * If it is incorrect, the method attempts to correct it by setting it to the given level.
     */
    public void correctSize(long maxNumber, Logger log) {
        // correcting the size if smaller than should be
        long storedSize = index.getStoredSize();
        if (maxNumber >= storedSize) {
            // can't change size directly, so we do a put + delete the next level to reset it
            index.set(maxNumber + 1, new ArrayList<>());
            index.remove(maxNumber + 1);
            log.info("Corrupted index size corrected from {} to {}.", storedSize, index.getStoredSize());
        }
    }

    /**
     * Sets the block as main chain and all its ancestors. Used by the data recovery methods.
     */
    public void correctMainChain(AionBlock block, Logger log) {
        lock.writeLock().lock();

        try {
            AionBlock currentBlock = block;
            if (currentBlock != null) {
                List<BlockInfo> infos = getBlockInfoForLevel(currentBlock.getNumber());
                BlockInfo thisBlockInfo = getBlockInfoForHash(infos, currentBlock.getHash());

                // loop stops when the block is null or is already main chain
                while (thisBlockInfo != null && !thisBlockInfo.isMainChain()) {
                    log.info("Setting block hash: {}, number: {} to main chain.",
                             currentBlock.getShortHash(),
                             currentBlock.getNumber());

                    // fix the info for the current block
                    infos.remove(thisBlockInfo);
                    thisBlockInfo.setMainChain(true);
                    infos.add(thisBlockInfo);
                    setBlockInfoForLevel(currentBlock.getNumber(), infos);

                    // fix the info for parent
                    currentBlock = getBlockByHash(currentBlock.getParentHash());
                    if (currentBlock != null) {
                        infos = getBlockInfoForLevel(currentBlock.getNumber());
                        thisBlockInfo = getBlockInfoForHash(infos, currentBlock.getHash());
                    } else {
                        thisBlockInfo = null;
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static class BlockInfo implements Serializable {

        public BlockInfo() {}

        public BlockInfo(byte[] ser) {
            RLPList outerList = RLP.decode2(ser);

            // should we throw?
            if (outerList.isEmpty()) { return; }

            RLPList list = (RLPList) outerList.get(0);
            this.hash = list.get(0).getRLPData();
            this.cummDifficulty = ByteUtil.bytesToBigInteger(list.get(1).getRLPData());

            byte[] boolData = list.get(2).getRLPData();
            this.mainChain = !(boolData == null || boolData.length == 0) && boolData[0] == (byte) 0x1;
        }

        private static final long serialVersionUID = 7279277944605144671L;

        private byte[] hash;

        private BigInteger cummDifficulty;

        private boolean mainChain;

        public byte[] getHash() {
            return hash;
        }

        public void setHash(byte[] hash) {
            this.hash = hash;
        }

        public BigInteger getCummDifficulty() {
            return cummDifficulty;
        }

        public void setCummDifficulty(BigInteger cummDifficulty) {
            this.cummDifficulty = cummDifficulty;
        }

        public boolean isMainChain() {
            return mainChain;
        }

        public void setMainChain(boolean mainChain) {
            this.mainChain = mainChain;
        }

        public byte[] getEncoded() {
            byte[] hashElement = RLP.encodeElement(hash);
            byte[] cumulativeDiffElement = RLP.encodeElement(cummDifficulty.toByteArray());
            byte[] mainChainElement = RLP.encodeByte(mainChain ? (byte) 0x1 : (byte) 0x0);
            return RLP.encodeList(hashElement, cumulativeDiffElement, mainChainElement);
        }
    }

    private static class MigrationRedirectingInputStream extends ObjectInputStream {

        public MigrationRedirectingInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (desc.getName().equals("org.aion.db.a0.AionBlockStore$BlockInfo")) { return BlockInfo.class; }
            return super.resolveClass(desc);
        }
    }

    /**
     * Called by {@link AionBlockStore#BLOCK_INFO_SERIALIZER} for now, on main-net launch
     * we should default to this class.
     */
    public static final Serializer<List<BlockInfo>, byte[]> BLOCK_INFO_RLP_SERIALIZER = new Serializer<>() {
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
                res.add(new BlockInfo(aList.getRLPData()));
            }
            return res;
        }
    };

    public static final Serializer<List<BlockInfo>, byte[]> BLOCK_INFO_SERIALIZER = new Serializer<>() {

        @Override
        public byte[] serialize(List<BlockInfo> value) {
            return BLOCK_INFO_RLP_SERIALIZER.serialize(value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<BlockInfo> deserialize(byte[] bytes) {
            try {
                return BLOCK_INFO_RLP_SERIALIZER.deserialize(bytes);
            } catch (Exception e) {
                // fallback logic for old block infos
                try {
                    ByteArrayInputStream bis = new ByteArrayInputStream(bytes, 0, bytes.length);
                    ObjectInputStream ois = new MigrationRedirectingInputStream(bis);
                    return (List<BlockInfo>) ois.readObject();
                } catch (IOException | ClassNotFoundException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
    };

    public void printChain() {
        lock.readLock().lock();

        try {
            Long number = getMaxNumber();

            for (int i = 0; i < number; ++i) {
                List<BlockInfo> levelInfos = index.get(i);

                if (levelInfos != null) {
                    System.out.print(i);
                    for (BlockInfo blockInfo : levelInfos) {
                        if (blockInfo.isMainChain()) {
                            System.out.print(" [" + shortHash(blockInfo.getHash()) + "] ");
                        } else {
                            System.out.print(" " + shortHash(blockInfo.getHash()) + " ");
                        }
                    }
                    System.out.println();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private List<BlockInfo> getBlockInfoForLevel(long level) {
        // locks acquired by calling method
        return index.get(level);
    }

    /**
     * @implNote The method calling this method must handle the locking.
     */
    private void setBlockInfoForLevel(long level, List<BlockInfo> infos) {
        // locks acquired by calling method
        index.set(level, infos);
    }

    /**
     * @return the hash information if it is present in the list
     *         or {@code null} when the given block list is {@code null}
     *         or the hash is not present in the list
     * @implNote The method calling this method must handle the locking.
     */
    private static BlockInfo getBlockInfoForHash(List<BlockInfo> blocks, byte[] hash) {
        if (blocks == null) {
            return null;
        }
        for (BlockInfo blockInfo : blocks) {
            if (Arrays.equals(hash, blockInfo.getHash())) {
                return blockInfo;
            }
        }
        return null;
    }

    @Override
    public void load() {
        if (checkIntegrity) {
            indexIntegrityCheck();
        }
    }

    public enum IntegrityCheckResult {
        MISSING_GENESIS,
        MISSING_LEVEL,
        FIXED,
        CORRECT
    }

    public IntegrityCheckResult indexIntegrityCheck() {
        if (index.size() > 0) {
            LOG_CONS.info("Checking the integrity of the total difficulty information...");

            // check each block's total difficulty till genesis
            boolean correct = true;
            AionBlock block = getBestBlock();
            while (correct && block.getNumber() > 0) {
                // it is correct if there is no inconsistency wrt to the parent
                correct = getTotalDifficultyForHash(block.getHash())
                        .equals(getTotalDifficultyForHash(block.getParentHash()).add(block.getDifficultyBI()));
                LOG_CONS.info("Total difficulty for block hash: {} number: {} is {}.",
                              block.getShortHash(),
                              block.getNumber(),
                              correct ? "OK" : "NOT OK");
                // check parent next
                block = getBlockByHash(block.getParentHash());
            }

            // check correct TD for genesis block
            if (block.getNumber() == 0) {
                correct = getTotalDifficultyForHash(block.getHash()).equals(block.getDifficultyBI());
                LOG_CONS.info("Total difficulty for block hash: {} number: {} is {}.",
                              block.getShortHash(),
                              block.getNumber(),
                              correct ? "OK" : "NOT OK");
            }

            // if any inconsistency, correct the TD
            if (!correct) {
                LOG_CONS.info("Integrity check of total difficulty found INVALID information. Correcting ...");

                List<BlockInfo> infos = getBlockInfoForLevel(0);
                if (infos == null) {
                    LOG_CONS.error("Missing genesis block information. Cannot recover without deleting database.");
                    return IntegrityCheckResult.MISSING_GENESIS;
                }

                for (BlockInfo bi : infos) {
                    block = getBlockByHash(bi.getHash());
                    bi.setCummDifficulty(block.getDifficultyBI());
                    LOG_CONS.info("Correcting total difficulty for block hash: {} number: {} to {}.",
                                  block.getShortHash(),
                                  block.getNumber(),
                                  bi.getCummDifficulty());
                }
                setBlockInfoForLevel(0, infos);

                long level = 1;

                do {
                    infos = getBlockInfoForLevel(level);
                    if (infos == null) {
                        LOG_CONS.error("Missing block information at level {}."
                                               + " Cannot recover without reverting to block number {}.",
                                       level,
                                       (level - 1));
                        return IntegrityCheckResult.MISSING_LEVEL;
                    }

                    for (BlockInfo bi : infos) {
                        block = getBlockByHash(bi.getHash());
                        bi.setCummDifficulty(block.getDifficultyBI()
                                                     .add(getTotalDifficultyForHash(block.getParentHash())));
                        LOG_CONS.info("Correcting total difficulty for block hash: {} number: {} to {}.",
                                      block.getShortHash(),
                                      block.getNumber(),
                                      bi.getCummDifficulty());
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
    }

    @Override
    public void close() {
        lock.writeLock().lock();

        try {
            indexDS.close();
        } catch (Exception e) {
            LOG.error("Not able to close the index database:", e);
        } finally {
            try {
                blocksDS.close();
            } catch (Exception e) {
                LOG.error("Not able to close the blocks database:", e);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}
