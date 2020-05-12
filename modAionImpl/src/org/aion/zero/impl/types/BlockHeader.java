package org.aion.zero.impl.types;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import org.aion.util.types.ByteArrayWrapper;

/**
 * The block header interface for cross-module usage purpose.
 * @author jay
 */
public interface BlockHeader {

    enum Seal {
        NOT_APPLICABLE((byte) 0),
        PROOF_OF_WORK((byte) 1),
        PROOF_OF_STAKE((byte) 2);

        final byte sealId;

        Seal(byte sealId) {
            this.sealId = sealId;
        }

        public byte getSealId() {
            return sealId;
        }

        public static Seal byteToSealType(byte id) {
            if (id == PROOF_OF_WORK.sealId) {
                return PROOF_OF_WORK;
            } else if (id == PROOF_OF_STAKE.sealId) {
                return PROOF_OF_STAKE;
            } else {
                return NOT_APPLICABLE;
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

    Seal getSealType();

    byte[] getTxTrieRoot();
    ByteArrayWrapper getTxTrieRootWrapper();
}
