package org.aion.mcf.blockchain;

import java.math.BigInteger;
import org.aion.interfaces.block.Block;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.types.Hash256;

/**
 * proof of work chain interface.
 *
 * @param <BLK>
 * @param <BH>
 */
@SuppressWarnings("rawtypes")
public interface IPowChain<BLK extends Block, BH extends AbstractBlockHeader>
        extends IGenericChain<BLK, BH> {

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);
}
