package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.util.types.Hash256;

/**
 * proof of work chain interface.
 *
 */
@SuppressWarnings("rawtypes")
public interface IPowChain extends IGenericChain {

    BigInteger getTotalMiningDifficulty();
    
    BigInteger getTotalStakingDifficulty();
    
    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Block createStakingBlockTemplate(List<AionTransaction> pendingTransactions, byte[] publicKey, byte[] seed);

    byte[] getSeed();
}
