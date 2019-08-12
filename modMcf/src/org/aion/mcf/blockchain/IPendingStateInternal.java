package org.aion.mcf.blockchain;

/**
 * Internal pending state interface.
 */
public interface IPendingStateInternal extends IPendingState {

    void shutDown();

    int getPendingTxSize();

    void updateBest();

    void DumpPool();

    void loadPendingTx();

    void checkAvmFlag();
}
