package org.aion.mcf.blockchain;

import java.util.List;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Internal pending state interface.
 *
 * @param <BLK>
 * @param <Tx>
 */
public interface IPendingStateInternal<BLK extends IBlock<?, ?>, Tx extends ITransaction>
        extends IPendingState<Tx> {

    // called by onBest
    void processBest(BLK block, List<? extends AbstractTxReceipt<Tx>> receipts);

    void shutDown();

    int getPendingTxSize();

    void updateBest();

    void DumpPool();

    void loadPendingTx();
}
