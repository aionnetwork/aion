package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.util.types.Hash256;
import org.aion.zero.impl.core.ImportResult;

public interface UnityChain {

    BigInteger getTotalDifficulty();

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    IBlockStoreBase getBlockStore();

    Block getBestBlock();

    void flush();

    byte[] getSeed();

    Block createStakingBlockTemplate(
        List<AionTransaction> pendingTransactions,
        byte[] publicKey,
        byte[] seed);

    Block getCachingStakingBlockTemplate(byte[] hash);

    ImportResult tryToConnect(Block block);

    Block getBlockWithInfoByHash(byte[] hash);

    Block getBestBlockWithInfo();
}
