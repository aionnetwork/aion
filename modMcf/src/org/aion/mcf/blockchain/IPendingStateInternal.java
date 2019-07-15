package org.aion.mcf.blockchain;

import java.util.List;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Internal pending state interface.
 *
 * @param <BLK>
 */
public interface IPendingStateInternal<BLK extends Block> extends IPendingState {

    // called by onBest
    void processBest(BLK block, List<? extends AbstractTxReceipt> receipts);

    void shutDown();

    int getPendingTxSize();

    void updateBest();

    void DumpPool();

    void loadPendingTx();

    void checkAvmFlag();
}
