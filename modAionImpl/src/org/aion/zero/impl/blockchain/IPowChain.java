package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.util.types.Hash256;

/**
 * proof of work chain interface.
 *
 */
public interface IPowChain {

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Block getBlockByNumber(long number);

    Block getBlockByHash(byte[] hash);

    IBlockStoreBase getBlockStore();

    Block getBestBlock();

    void flush();
}
