/*******************************************************************************
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
 ******************************************************************************/

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

import static java.math.BigInteger.ZERO;
import static org.aion.crypto.HashUtil.shortHash;

public class AionBlockStore extends AbstractPowBlockstore<AionBlock, A0BlockHeader> {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    private IByteArrayKeyValueDatabase indexDS;
    private DataSourceArray<List<BlockInfo>> index;
    private IByteArrayKeyValueDatabase blocksDS;
    private ObjectDataSource<AionBlock> blocks;

    public AionBlockStore(IByteArrayKeyValueDatabase index, IByteArrayKeyValueDatabase blocks) {
        init(index, blocks);
    }

    private void init(IByteArrayKeyValueDatabase index, IByteArrayKeyValueDatabase blocks) {

        indexDS = index;
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
    }

    public byte[] getBlockHashByNumber(long blockNumber) {

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
    }

    @Override
    public void flush() {
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
    }

    @Override
    public void saveBlock(AionBlock block, BigInteger cummDifficulty, boolean mainChain) {
        addInternalBlock(block, cummDifficulty, mainChain);
    }

    private void addInternalBlock(AionBlock block, BigInteger cummDifficulty, boolean mainChain) {

        List<BlockInfo> blockInfos =
                block.getNumber() >= index.size() ? new ArrayList<>() : index.get(block.getNumber());

        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setCummDifficulty(cummDifficulty);
        blockInfo.setHash(block.getHash());
        blockInfo.setMainChain(mainChain); // FIXME: maybe here I should force reset main chain for all uncles on that level

        blockInfos.add(blockInfo);

        blocks.put(block.getHash(), block);
        index.set(block.getNumber(), blockInfos);
    }

    public List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> getBlocksByNumber(long number) {

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
    }

    @Override
    public AionBlock getChainBlockByNumber(long number) {
        long size = index.size();
        if (number < 0L || number >= size) {
            return null;
        }

        List<BlockInfo> blockInfos = index.get(number);

        for (BlockInfo blockInfo : blockInfos) {

            if (blockInfo.isMainChain()) {

                byte[] hash = blockInfo.getHash();
                return blocks.get(hash);
            }
        }

        return null;
    }

    @Override
    public AionBlock getBlockByHash(byte[] hash) {
        return blocks.get(hash);
    }

    @Override
    public boolean isBlockExist(byte[] hash) {
        return blocks.get(hash) != null;
    }

    @Override
    public BigInteger getTotalDifficultyForHash(byte[] hash) {
        IAionBlock block = this.getBlockByHash(hash);
        if (block == null) {
            return ZERO;
        }

        Long level = block.getNumber();
        List<BlockInfo> blockInfos = index.get(level.intValue());
        for (BlockInfo blockInfo : blockInfos) {
            if (Arrays.equals(blockInfo.getHash(), hash)) {
                return blockInfo.cummDifficulty;
            }
        }

        return ZERO;
    }

    @Override
    public BigInteger getTotalDifficulty() {
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
    }

    @Override
    public long getMaxNumber() {
        // the index size is always >= 0
        long bestIndex = index.size();
        return bestIndex - 1L;
    }

    @Override
    public List<byte[]> getListHashesEndWith(byte[] hash, long number) {

        List<AionBlock> blocks = getListBlocksEndWith(hash, number);
        List<byte[]> hashes = new ArrayList<>(blocks.size());

        for (IAionBlock b : blocks) {
            hashes.add(b.getHash());
        }

        return hashes;
    }

    @Override
    public List<A0BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {

        List<AionBlock> blocks = getListBlocksEndWith(hash, qty);
        List<A0BlockHeader> headers = new ArrayList<>(blocks.size());

        for (IAionBlock b : blocks) {
            headers.add(b.getHeader());
        }

        return headers;
    }

    @Override
    public List<AionBlock> getListBlocksEndWith(byte[] hash, long qty) {
        return getListBlocksEndWithInner(hash, qty);
    }

    private List<AionBlock> getListBlocksEndWithInner(byte[] hash, long qty) {

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
                    LOG.error("Null block information found at " + currentLevel + " when information should exist.");
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
                    LOG.error("Null block information found at " + currentLevel + " when information should exist.");
                }
                bestLine = getBlockByHash(bestLine.getParentHash());
                --currentLevel;
            }
        }

        // 2. Loop back on each level until common block
        loopBackToCommonBlock(bestLine, forkLine);
    }

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

        AionLoggerFactory.getLogger(LogEnum.CONS.name()).info("branching: common block = {}/{}", forkLine.getNumber(), Hex.toHexString(forkLine.getHash()));
    }

    @Override
    public void revert(long previousLevel) {

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
                    LOG.error("Null block information found at " + previousLevel + " when information should exist. "
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
    }

    @Override
    public void pruneAndCorrect() {
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
        BigInteger parentTotalDifficulty = block.getCumulativeDifficulty();
        level = 1;
        while (level <= initialLevel) {
            parentTotalDifficulty = correctTotalDifficulty(level, parentTotalDifficulty);
            LOG.info("Updated total difficulty on level " + level + " to " + parentTotalDifficulty + ".");
            level++;
        }
    }

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

        // set new block info without total difficulty
        blockInfo = new BlockInfo();
        blockInfo.setCummDifficulty(block.getHeader().getDifficultyBI());
        blockInfo.setHash(blockHash);
        blockInfo.setMainChain(true);

        levelBlocks = new ArrayList<>();
        levelBlocks.add(blockInfo);

        setBlockInfoForLevel(level, levelBlocks);
    }

    private BigInteger correctTotalDifficulty(long level, BigInteger parentTotalDifficulty) {
        List<BlockInfo> levelBlocks = getBlockInfoForLevel(level);

        if (levelBlocks.size() != 1) {
            // something went awry
            LOG.error("Cannot proceed with total difficulty updates. Previous updates have been overwritten.");
            return null;
        } else {
            // correct block info
            BlockInfo blockInfo = levelBlocks.remove(0);
            blockInfo.setCummDifficulty(blockInfo.getCummDifficulty().add(parentTotalDifficulty));
            levelBlocks.add(blockInfo);
            setBlockInfoForLevel(level, levelBlocks);

            return blockInfo.getCummDifficulty();
        }
    }

    public void dumpPastBlocks(long numberOfBlocks, String reportsFolder) throws IOException {
        long firstBlock = getMaxNumber();
        long lastBlock = firstBlock - numberOfBlocks;

        File file = new File(reportsFolder, System.currentTimeMillis() + "-blocks-report.out");

        BufferedWriter writer = new BufferedWriter(new FileWriter(file));

        while (firstBlock > lastBlock && firstBlock >= 0) {
            List<BlockInfo> levelBlocks = getBlockInfoForLevel(firstBlock);

            writer.append("Blocks at level " + firstBlock + ":");
            writer.newLine();

            for (BlockInfo bi : levelBlocks) {
                writer.append(
                        "Hash: " + Hex.toHexString(bi.getHash()) + " Total Difficulty: " + bi.getCummDifficulty());
                writer.newLine();
            }
            writer.newLine();

            firstBlock--;
        }

        writer.close();
    }

    public List<byte[]> getListHashesStartWith(long number, long maxBlocks) {

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

        public byte[] hash;

        public BigInteger cummDifficulty;

        public boolean mainChain;

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
    }

    private List<BlockInfo> getBlockInfoForLevel(long level) {
        return index.get(level);
    }

    private void setBlockInfoForLevel(long level, List<BlockInfo> infos) {
        index.set(level, infos);
    }

    private static BlockInfo getBlockInfoForHash(List<BlockInfo> blocks, byte[] hash) {

        for (BlockInfo blockInfo : blocks) {
            if (Arrays.equals(hash, blockInfo.getHash())) {
                return blockInfo;
            }
        }

        return null;
    }

    @Override
    public void load() {
    }

    @Override
    public void close() {
        try {
            indexDS.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            blocksDS.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
