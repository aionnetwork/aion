package org.aion.zero.impl.core;

import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.mcf.core.IBlockchain;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

/** aion blockchain interface. */
public interface IAionBlockchain
        extends IBlockchain<AionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {

    AionBlock createNewBlock(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    BlockContext createNewBlockContext(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    AionBlock getBestBlock();

    AionBlock getBlockByNumber(long num);

    /**
     * Recovery functionality for rebuilding the world state.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverWorldState(IRepository repository, AionBlock block);

    /**
     * Recovery functionality for recreating the block info in the index database.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverIndexEntry(IRepository repository, AionBlock block);

    /**
     * Heuristic for skipping the call to tryToConnect with very large or very small block number.
     */
    boolean skipTryToConnect(long blockNumber);
}
