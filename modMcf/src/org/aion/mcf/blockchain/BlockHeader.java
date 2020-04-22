package org.aion.mcf.blockchain;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import org.aion.util.types.ByteArrayWrapper;

/**
 * The block header interface for cross-module usage purpose.
 * @author jay
 */
public interface BlockHeader {

    enum BlockSealType {
        SEAL_NA((byte) 0),
        SEAL_POW_BLOCK((byte) 1),
        SEAL_POS_BLOCK((byte) 2);

        final byte sealId;

        BlockSealType(byte sealId) {
            this.sealId = sealId;
        }

        public byte getSealId() {
            return sealId;
        }

        public static BlockSealType byteToSealType(byte id) {
            if (id == SEAL_POW_BLOCK.sealId) {
                return SEAL_POW_BLOCK;
            } else if (id == SEAL_POS_BLOCK.sealId) {
                return SEAL_POS_BLOCK;
            } else {
                return SEAL_NA;
            }
        }
    }

    int HASH_BYTE_SIZE = 32;
    int BLOOM_BYTE_SIZE = 256;
    int MAX_DIFFICULTY_LENGTH = 16;

    byte[] getParentHash();
    ByteArrayWrapper getParentHashWrapper();

    byte[] getExtraData();

    byte[] getHash();
    ByteArrayWrapper getHashWrapper();

    byte[] getEncoded();

    long getTimestamp();

    long getNumber();

    boolean isGenesis();

    @VisibleForTesting
    byte[] getDifficulty();

    BigInteger getDifficultyBI();

    long getEnergyConsumed();

    long getEnergyLimit();

    byte[] getMineHash();

    BlockSealType getSealType();

    byte[] getTxTrieRoot();
}
