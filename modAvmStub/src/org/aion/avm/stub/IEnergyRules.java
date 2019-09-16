package org.aion.avm.stub;

/**
 * The rules around transaction energy limits.
 */
@FunctionalInterface
public interface IEnergyRules {
    public enum TransactionType { CREATE, NON_CREATE }

    /**
     * Returns {@code true} only if the specified limit is a valid energy limit for a transaction
     * whose transaction type is the specified type. Returns {@code false} otherwise.
     *
     * @param transactionType The transaction type.
     * @param limit The energy limit.
     * @return whether the energy limit is valid.
     */
    public boolean isValidEnergyLimit(TransactionType transactionType, long limit);
}
