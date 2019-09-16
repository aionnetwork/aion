package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.stake.StakeRunnerInterface;
import org.aion.util.types.Hash256;

/**
 * proof of work chain interface.
 *
 */
@SuppressWarnings("rawtypes")
public interface UnityChain {
    
    BigInteger getTotalDifficulty();

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Block createStakingBlockTemplate(
            List<AionTransaction> pendingTransactions,
            byte[] publicKey,
            byte[] seed);

    Block getCachingStakingBlockTemplate(byte[] hash);

    boolean putSealedNewStakingBlock(Block block);

    byte[] getSeed();

    Block getBestBlockWithInfo();

    Block getBlockWithInfoByHash(byte[] hash);

    StakeRunnerInterface getStakeRunner();

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    IBlockStoreBase getBlockStore();

    Block getBestBlock();

    void flush();
}
