package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.zero.impl.types.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.StakingBlock;

public interface UnityChain {

    BigInteger getTotalDifficulty();

    BigInteger getTotalDifficultyForHash(byte[] hash);

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    Block getBestBlock();

    void flush();

    byte[] getSeed();

    StakingBlock createStakingBlockTemplate(
        Block parent,
        List<AionTransaction> pendingTransactions,
        byte[] publicKey,
        byte[] seed,
        byte[] coinbase);

    BlockContext createNewMiningBlockContext(Block parent, List<AionTransaction> txs, boolean waitUntilBlockTime);

    StakingBlock getCachingStakingBlockTemplate(byte[] hash);

    MiningBlock getCachingMiningBlockTemplate(byte[] hash);

    ImportResult tryToConnect(Block block);

    ImportResult tryToConnect(BlockWrapper blockWrapper);

    Block getBlockWithInfoByHash(byte[] hash);

    Block getBestBlockWithInfo();

    StakingBlock getBestStakingBlock();

    MiningBlock getBestMiningBlock();

    boolean isUnityForkEnabledAtNextBlock();

    BigInteger calculateBlockRewards(long block_number);

    boolean isSignatureSwapForkEnabledAtNextBlock();
}
