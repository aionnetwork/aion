package org.aion.mcf.db;

import java.util.List;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;

/**
 * BlockStore interface base.
 *
 */
public interface IBlockStoreBase {

    byte[] getBlockHashByNumber(long blockNumber);

    byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash);

    Block getChainBlockByNumber(long blockNumber);

    Block getBlockByHash(byte[] hash);

    boolean isBlockStored(byte[] hash, long number);

    List<byte[]> getListHashesEndWith(byte[] hash, long qty);

    List<BlockHeader> getListHeadersEndWith(byte[] hash, long qty);

    List<Block> getListBlocksEndWith(byte[] hash, long qty);

    Block getBestBlock();

    long getMaxNumber();

    void flush();

    /** @return the common block that was found during the re-branching */
    long reBranch(Block forkBlock);

    void revert(long previousLevel);

    void pruneAndCorrect();

    void load();

    void close();

    void rollback(long blockNumber);
}
