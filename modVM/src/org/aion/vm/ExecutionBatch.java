package org.aion.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.fastvm.ExecutionContext;
import org.aion.mcf.vm.types.DataWord;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.ArrayUtils;

/**
 * A class that holds details related to a particular block that are required by the virtual
 * machines for transaction processing.
 *
 * <p>Each block has the following two details associated with it: 1. A list of transactions
 * belonging to that block 2. A list of transaction contexts
 *
 * <p>The following invariants are guaranteed by this holder class: - The number of transactions
 * equals the number of contexts - The context at index {@code i} in the context list corresponds to
 * the transaction at index {@code i} in the transaction list, and vice versa.
 *
 * <p>NOTE: The virtual machines are allowed to make the following assumptions about the
 * transactions and contexts in this holder: - Every transaction in this holder belongs to its
 * specified block. - The transactions are ordered in their list in the same order in which they
 * must be logically processed, where index 0 indicates the first transaction to be processed.
 */
public final class ExecutionBatch {
    private List<AionTransaction> transactions;
    private List<KernelTransactionContext> contexts;
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
        this.contexts = constructTransactionContexts(this.transactions, this.block);
    }

    private ExecutionBatch(
            IAionBlock block,
            List<AionTransaction> transactions,
            List<KernelTransactionContext> contexts) {
        this.block = block;
        this.transactions = transactions;
        this.contexts = contexts;
    }

    /**
     * Returns a slice of this BlockDetails object over the range [start, stop).
     *
     * <p>The slice pertains to the same block but only retains the transactions and their
     * corresponding contexts within the specified index range.
     *
     * @param start The index of the first transaction in the slice, inclusive.
     * @param stop The index of the last transaction in the slice, exclusive.
     * @return The sliced version of this BlockDetails object.
     */
    public ExecutionBatch slice(int start, int stop) {
        List<AionTransaction> transactions = this.transactions.subList(start, stop);
        List<KernelTransactionContext> contexts = this.contexts.subList(start, stop);
        return new ExecutionBatch(this.block, transactions, contexts);
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

    /**
     * Returns the execution contexts for the transactions in this block.
     *
     * @return The contexts.
     */
    public KernelTransactionContext[] getExecutionContexts() {
        KernelTransactionContext[] contextsAsArray =
                new KernelTransactionContext[this.contexts.size()];
        this.contexts.toArray(contextsAsArray);
        return contextsAsArray;
    }

    public int size() {
        return this.transactions.size();
    }

    private List<KernelTransactionContext> constructTransactionContexts(
            List<AionTransaction> transactions, IAionBlock block) {
        List<KernelTransactionContext> contexts = new ArrayList<>();
        for (AionTransaction transaction : transactions) {
            contexts.add(constructTransactionContext(transaction, block));
        }
        return contexts;
    }

    private KernelTransactionContext constructTransactionContext(
            AionTransaction transaction, IAionBlock block) {
        byte[] txHash = transaction.getTransactionHash();
        Address address =
                transaction.isContractCreationTransaction()
                        ? transaction.getContractAddress() // or should this be null?
                        : transaction.getDestinationAddress();
        Address origin = transaction.getSenderAddress();
        Address caller = transaction.getSenderAddress();

        DataWord nrgPrice = transaction.nrgPrice();
        long energyLimit = transaction.nrgLimit();
        long nrg = transaction.nrgLimit() - transaction.transactionCost(block.getNumber());
        DataWord callValue = new DataWord(ArrayUtils.nullToEmpty(transaction.getValue()));
        byte[] callData =
                transaction.isContractCreationTransaction()
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : ArrayUtils.nullToEmpty(transaction.getData());

        int depth = 0;
        int kind =
                transaction.isContractCreationTransaction()
                        ? ExecutionContext.CREATE
                        : ExecutionContext.CALL;
        int flags = 0;

        Address blockCoinbase = block.getCoinbase();
        long blockNumber = block.getNumber();
        long blockTimestamp = block.getTimestamp();
        long blockNrgLimit = block.getNrgLimit();

        // TODO: temp solution for difficulty length
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        DataWord blockDifficulty = new DataWord(diff);

        return new KernelTransactionContext(
                transaction,
                txHash,
                address,
                origin,
                caller,
                nrgPrice,
                nrg,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }
}
