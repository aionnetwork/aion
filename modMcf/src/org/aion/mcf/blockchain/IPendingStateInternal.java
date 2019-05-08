package org.aion.mcf.blockchain;

import java.util.List;
import org.aion.interfaces.block.Block;
import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Internal pending state interface.
 *
 * @param <BLK>
 * @param <Tx>
 */
public interface IPendingStateInternal<BLK extends Block<?, ?>, Tx extends Transaction>
        extends IPendingState<Tx> {

    // called by onBest
    void processBest(BLK block, List<? extends AbstractTxReceipt<Tx>> receipts);

    void shutDown();

    int getPendingTxSize();

    void updateBest();

    void DumpPool();

    void loadPendingTx();

    void checkAvmFlag();
}
