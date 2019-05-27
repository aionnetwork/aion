package org.aion.vm;

import java.util.List;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;

/**
 * A class that holds details related to a particular block that are required by the virtual
 * machines for transaction processing.
 *
 * <p>Each block has the following two details associated with it: 1. A list of transactions
 * belonging to that block.
 *
 * <p>NOTE: The virtual machines are allowed to make the following assumptions about the
 * transactions and contexts in this holder: - Every transaction in this holder belongs to its
 * specified block. - The transactions are ordered in their list in the same order in which they
 * must be logically processed, where index 0 indicates the first transaction to be processed.
 */
public final class ExecutionBatch {
    private List<AionTransaction> transactions;
    private IAionBlock block;

    public ExecutionBatch(IAionBlock block, List<AionTransaction> transactions) {
        if (block == null) {
            throw new NullPointerException("Cannot construct BlockDetails with null block.");
        }
        if (transactions == null) {
            throw new NullPointerException("Cannot construct BlockDetails with null transactions.");
        }

        this.block = block;
        this.transactions = transactions;
    }

    /**
     * Returns a slice of this BlockDetails object over the range [start, stop).
     *
     * <p>The slice pertains to the same block but only retains the transactions within the specified index range.
     *
     * @param start The index of the first transaction in the slice, inclusive.
     * @param stop The index of the last transaction in the slice, exclusive.
     * @return The sliced version of this BlockDetails object.
     */
    ExecutionBatch slice(int start, int stop) {
        List<AionTransaction> transactions = this.transactions.subList(start, stop);
        return new ExecutionBatch(this.block, transactions);
    }

    /**
     * Returns the block.
     *
     * @return The block.
     */
    public IAionBlock getBlock() {
        return this.block;
    }

    /**
     * Returns the transactions for this block.
     *
     * @return The transactions.
     */
    public List<AionTransaction> getTransactions() {
        return this.transactions;
    }

    public int size() {
        return this.transactions.size();
    }
}
