package org.aion.mcf.db;

import java.math.BigInteger;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * POW BLockstore interface.
 *
 * @param <BLK>
 * @param <BH>
 */
public interface IBlockStorePow<BLK extends IBlock<?, ?>, BH extends AbstractBlockHeader>
        extends IBlockStoreBase<BLK, BH> {

    BigInteger getTotalDifficultyForHash(byte[] hash);

    void saveBlock(BLK block, BigInteger cummDifficulty, boolean mainChain);

    BigInteger getTotalDifficulty();
}
