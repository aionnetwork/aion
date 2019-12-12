package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.util.types.Hash256;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.StakingBlock;

public interface UnityChain {

    BigInteger getTotalDifficulty();

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    AionBlockStore getBlockStore();

    Block getBestBlock();

    void flush();

    byte[] getSeed();

    Block createStakingBlockTemplate(
        List<AionTransaction> pendingTransactions,
        byte[] publicKey,
        byte[] seed,
        byte[] coinbase);

    StakingBlock getCachingStakingBlockTemplate(byte[] hash);

    AionBlock getCachingMiningBlockTemplate(byte[] hash);

    ImportResult tryToConnect(Block block);

    Block getBlockWithInfoByHash(byte[] hash);

    Block getBestBlockWithInfo();

    StakingBlock getBestStakingBlock();

    AionBlock getBestMiningBlock();

    void loadBestStakingBlock();
    
    void loadBestMiningBlock();
    
    boolean isUnityForkEnabledAtNextBlock();

    BigInteger calculateBlockRewards(long block_number);
}
