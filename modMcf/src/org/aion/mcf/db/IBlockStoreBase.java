package org.aion.mcf.db;

import java.util.List;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.types.AbstractBlockHeader;

/**
 * BlockStore interface base.
 *
 * @param <BLK>
 * @param <BH>
 */
public interface IBlockStoreBase<BLK extends Block, BH extends AbstractBlockHeader> {

    byte[] getBlockHashByNumber(long blockNumber);

    byte[] getBlockHashByNumber(long blockNumber, byte[] branchBlockHash);

    BLK getChainBlockByNumber(long blockNumber);

    BLK getBlockByHash(byte[] hash);

    boolean isBlockExist(byte[] hash);

    List<byte[]> getListHashesEndWith(byte[] hash, long qty);

    List<BH> getListHeadersEndWith(byte[] hash, long qty);

    List<BLK> getListBlocksEndWith(byte[] hash, long qty);

    BLK getBestBlock();

    long getMaxNumber();

    void flush();

    /** @return the common block that was found during the re-branching */
    long reBranch(BLK forkBlock);

    void revert(long previousLevel);

    void pruneAndCorrect();

    void load();

    void close();

    void rollback(long blockNumber);
}
