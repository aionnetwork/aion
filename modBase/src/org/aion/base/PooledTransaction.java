package org.aion.base;

public class PooledTransaction {
    public final AionTransaction tx;
    public final long energyConsumed;

    public PooledTransaction(AionTransaction aionTransaction, long energyConsumed) {
        this.tx = aionTransaction;
        this.energyConsumed = energyConsumed;
    }
}
