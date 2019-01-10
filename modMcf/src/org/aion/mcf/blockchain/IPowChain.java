package org.aion.mcf.blockchain;

import java.math.BigInteger;
import org.aion.base.type.Hash256;
import org.aion.base.type.IBlock;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * proof of work chain interface.
 *
 * @param <BLK>
 * @param <BH>
 */
@SuppressWarnings("rawtypes")
public interface IPowChain<BLK extends IBlock, BH extends AbstractBlockHeader>
        extends IGenericChain<BLK, BH> {

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);
}
