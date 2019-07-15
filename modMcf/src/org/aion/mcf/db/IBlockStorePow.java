package org.aion.mcf.db;

import java.math.BigInteger;
import org.aion.mcf.blockchain.Block;

/**
 * POW BLockstore interface.
 *
 */
public interface IBlockStorePow extends IBlockStoreBase {

    BigInteger getTotalDifficultyForHash(byte[] hash);

    void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain);

    BigInteger getTotalDifficulty();
}
