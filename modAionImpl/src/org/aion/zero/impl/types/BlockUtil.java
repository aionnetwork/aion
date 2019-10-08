package org.aion.zero.impl.types;

import java.util.Arrays;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.slf4j.Logger;

/**
 * Utility for creating {@link Block} objects. The static methods in this class should be used
 * instead of instantiating the different block types using their constructors directly.
 *
 * @author Alexandra Roatis
 */
public final class BlockUtil {
    private static final Logger genLog = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    private BlockUtil() {
        throw new IllegalStateException("This utility class is not meant to be instantiated.");
    }

    /**
     * Decodes the given encoding into a new instance of a block or returns {@code null} if the RLP
     * encoding does not describe a valid block.
     *
     * @param rlp RLP encoded block data
     * @return a new instance of a block or {@code null} if the RLP encoding does not describe a
     *     valid block
     */
    public static Block newBlockFromRlp(byte[] rlp) {
        // return null when given empty bytes
        if (rlp == null || rlp.length == 0) {
            return null;
        }

        // attempt decoding, return null if it fails
        try {
            RLPList params = RLP.decode2(rlp);
            RLPList block = (RLPList) params.get(0);
            RLPList header = (RLPList) block.get(0);
            byte[] sealType = header.get(0).getRLPData();
            if (sealType[0] == BlockSealType.SEAL_POW_BLOCK.getSealId()) {
                return new AionBlock(rlp);
            } else if (sealType[0] == BlockSealType.SEAL_POS_BLOCK.getSealId()) {
                return new StakingBlock(rlp);
            } else {
                return null;
            }
        } catch (Exception e) {
            genLog.warn("Unable to decode block bytes " + Arrays.toString(rlp), e);
            return null;
        }
    }

    /**
     * Decodes the given encoding into a new instance of a block or returns {@code null} if the RLP
     * encoding does not describe a valid block.
     *
     * @param rlpList an RLPList instance encoding block data
     * @return a new instance of a block or {@code null} if the RLP encoding does not describe a
     *     valid block
     */
    public static Block newBlockFromRlpList(RLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null || rlpList.size() != 2) {
            return null;
        }
        try {
            // parse header
            RLPList headerRLP = (RLPList) rlpList.get(0);
            byte[] type = headerRLP.get(0).getRLPData();
            if (type[0] == BlockSealType.SEAL_POW_BLOCK.getSealId()) {
                return AionBlock.fromRLPList(rlpList);

            } else if (type[0] == BlockSealType.SEAL_POS_BLOCK.getSealId()) {
                return StakingBlock.fromRLPList(rlpList);
            } else {
                return null;
            }
        } catch (Exception e) {
            genLog.warn("Unable to decode block bytes " + Arrays.toString(rlpList.getRLPData()), e);
            return null;
        }
    }

    /**
     * Assembles a new block instance given its header and body. Returns {@code null} when given
     * invalid data.
     *
     * @param header the block header
     * @param bodyBytes the body of the block; can be an empty byte array when there are no
     *     transactions in the block
     * @return a new instance of a block or {@code null} when given invalid data
     */
    public static Block newBlockWithHeader(BlockHeader header, byte[] bodyBytes) {
        // return null when given empty bytes
        if (header == null || bodyBytes == null) {
            return null;
        }
        try {
            if (header.getSealType() == BlockSealType.SEAL_POW_BLOCK) {
                return AionBlock.createBlockFromNetwork((A0BlockHeader) header, bodyBytes);
            } else if (header.getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                return StakingBlock.createBlockFromNetwork((StakingBlockHeader) header, bodyBytes);
            } else {
                return null;
            }
        } catch (Exception e) {
            genLog.warn("Unable to decode block with header " + header, e);
            return null;
        }
    }

    /**
     * Decodes the given encoding into a new instance of a block header or returns {@code null} if
     * the RLP encoding does not describe a valid block header.
     *
     * @param rlpList an RLPList instance encoding block header data
     * @return a new instance of a block header or {@code null} if the RLP encoding does not
     *     describe a valid block header
     */
    public static BlockHeader newHeaderFromRlpList(RLPList rlpList) {
        // return null when given empty bytes
        if (rlpList == null) {
            return null;
        }

        // attempt decoding, return null if it fails
        try {
            byte[] sealType = rlpList.get(0).getRLPData();
            if (sealType[0] == BlockSealType.SEAL_POW_BLOCK.getSealId()) {
                return A0BlockHeader.Builder.newInstance(true).withRlpList(rlpList).build();
            } else if (sealType[0] == BlockSealType.SEAL_POS_BLOCK.getSealId()) {
                return StakingBlockHeader.Builder.newInstance(true).withRlpList(rlpList).build();
            } else {
                return null;
            }
        } catch (Exception e) {
            genLog.warn("Unable to decode block bytes " + Arrays.toString(rlpList.getRLPData()), e);
            return null;
        }
    }
}
