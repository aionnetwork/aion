package org.aion.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.avm.core.ExecutionType;
import org.aion.avm.core.FutureResult;
import org.aion.avm.core.IExternalState;
import org.aion.base.AionTransaction;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.vm.DataWord;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;

/**
 * A class that executes transactions that are to be run by the AVM.
 *
 * <p>This class should only ever be called by {@link BulkExecutor}!
 *
 * <p>This class is thread-safe.
 */
public final class AvmTransactionExecutor {

    /**
     * Executes the specified array of transactions using the AVM and returns a list of transaction
     * summaries, such that the i'th summary pertains to the i'th transaction in the input.
     *
     * <p>If no post-execution work is specified then none will be run. Otherwise, the
     * post-execution work will be applied in such a way that it appears, from the caller's
     * perspective, as if it was run immediately after each transaction sequentially.
     *
     * <p>This method performs no checks on its input data -- this is the responsibility of the
     * caller!
     *
     * <p>The only input that can be {@code null} is {@code postExecutionWork}.
     *
     * @param repository The current snapshot of the kernel's repository layer.
     * @param blockDifficulty The current best block's difficulty.
     * @param blockNumber The current best block number.
     * @param blockTimestamp The current best block timestamp.
     * @param blockNrgLimit The current best block energy limit.
     * @param blockCoinbase The address of the miner.
     * @param transactions The transactions to execute.
     * @param postExecutionWork The post-execute work, if any, to be run immediately after each
     *     transaction completes.
     * @param logger A logger.
     * @param decrementBlockEnergyLimit Whether to decrement the block energy limit (ie. if no
     *     decrement implication is block overflow won't be checked)
     * @param allowNonceIncrement Whether to increment the sender nonce.
     * @param isLocalCall Whether this is a local call or not.
     * @param initialBlockEnergyLimit The initial block energy limit at the time of running these
     *     transactions.
     * @param executionType indicates to the AVM the purpose for the transaction execution
     * @param cachedBlockNumber represents a main chain block that is common to the current main
     *     chain and the block that is about to be imported used for cache retrieval
     * @return a list of transaction summaries pertaining to the transactions.
     */
    public static List<AionTxExecSummary> executeTransactions(
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            AionTransaction[] transactions,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean decrementBlockEnergyLimit,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            long initialBlockEnergyLimit,
            ExecutionType executionType,
            long cachedBlockNumber)
            throws VMException {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();

        long blockRemainingEnergy = initialBlockEnergyLimit;

        AionVirtualMachine avm = LongLivedAvm.singleton();
        IExternalState kernel =
                new ExternalStateForAvm(
                        repository.startTracking(),
                        allowNonceIncrement,
                        isLocalCall,
                        getDifficultyAsDataWord(blockDifficulty),
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockCoinbase);

        try {
            // Acquire the avm lock and then run the transactions.
            Transaction[] avmTransactions = convertKernelTransactionsToAvm(transactions);
            avm.acquireAvmLock();
            FutureResult[] resultsAsFutures =
                    avm.run(kernel, avmTransactions, executionType, cachedBlockNumber);

            // Process the results of the transactions.
            int index = 0;
            for (FutureResult resultAsFuture : resultsAsFutures) {
                TransactionResult result = resultAsFuture.getResult();

                if (result.transactionStatus.isFatal()) {
                    throw new VMException(result.toString());
                }

                AionTransaction transaction = transactions[index];

                // Check the block energy limit & reject if necessary.
                if (result.energyUsed > blockRemainingEnergy) {
                    result = createCopyEnergyLimitExceeded(result, transaction.getEnergyLimit());
                }

                // Build the transaction summary.
                AionTxExecSummary summary = buildTransactionSummary(transaction, result);

                // Update the repository by committing any changes in the Avm.
                IExternalState externalState = resultAsFuture.getExternalState();

                // Note that the vm may have returned SUCCESS (and so there may be state changes
                // here to
                // commit) but the block limit check turned this to REJECTED. Now we do not want the
                // changes.
                if (!result.transactionStatus.isRejected()) {
                    externalState.commitTo(
                            new ExternalStateForAvm(
                                    repository,
                                    allowNonceIncrement,
                                    isLocalCall,
                                    getDifficultyAsDataWord(blockDifficulty),
                                    blockNumber,
                                    blockTimestamp,
                                    blockNrgLimit,
                                    blockCoinbase));
                }

                // Do any post execution work if any is specified.
                if (postExecutionWork != null) {
                    postExecutionWork.doWork(repository, summary, transaction);
                }

                // Update the remaining block energy.
                if (!result.transactionStatus.isRejected()) {
                    blockRemainingEnergy -=
                            ((decrementBlockEnergyLimit)
                                    ? summary.getReceipt().getEnergyUsed()
                                    : 0);
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Transaction receipt: {}", summary.getReceipt());
                    logger.debug("Transaction logs: {}", summary.getLogs());
                }

                transactionSummaries.add(summary);
                index++;
            }
        } catch (Throwable t) {
            throw t;
        } finally {
            avm.releaseAvmLock();
        }

        return transactionSummaries;
    }

    private static AionTxExecSummary buildTransactionSummary(
            AionTransaction transaction, TransactionResult result) {
        List<Log> logs = result.transactionStatus.isSuccess() ? result.logs : new ArrayList<>();
        byte[] output = result.copyOfTransactionOutput().orElse(ByteUtil.EMPTY_BYTE_ARRAY);

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result, output))
                        .logs(logs)
                        .deletedAccounts(new ArrayList<>())
                        .internalTransactions(result.internalTransactions)
                        .result(output);

        TransactionStatus resultCode = result.transactionStatus;
        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        return builder.build();
    }

    private static AionTxReceipt makeReceipt(
            AionTransaction transaction, List<Log> logs, TransactionResult result, byte[] output) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(result.energyUsed);
        receipt.setExecutionResult(output);
        receipt.setError(result.transactionStatus.causeOfError);
        return receipt;
    }

    /**
     * Converts the Kernel AionTransaction implementation into an AVM Transaction implementation.
     *
     * <p>This should be a temporary measure until both are using the exact same type.
     *
     * @param aionTransactions The Kernel Transactions.
     * @return The equivalent AVM Transactions.
     */
    private static Transaction[] convertKernelTransactionsToAvm(
            AionTransaction[] aionTransactions) {
        Transaction[] txs = new Transaction[aionTransactions.length];
        for (int i = 0; i < aionTransactions.length; i++) {
            AionTransaction tx = aionTransactions[i];
            if (tx.isContractCreationTransaction()) {
                txs[i] =
                        Transaction.contractCreateTransaction(
                                tx.getSenderAddress(),
                                tx.getTransactionHash(),
                                tx.getNonceBI(),
                                tx.getValueBI(),
                                tx.getData(),
                                tx.getEnergyLimit(),
                                tx.getEnergyPrice());
            } else {
                txs[i] =
                        Transaction.contractCallTransaction(
                                tx.getSenderAddress(),
                                tx.getDestinationAddress(),
                                tx.getTransactionHash(),
                                tx.getNonceBI(),
                                tx.getValueBI(),
                                tx.getData(),
                                tx.getEnergyLimit(),
                                tx.getEnergyPrice());
            }
        }
        return txs;
    }

    /**
     * Method that creates an almost identical copy of the original TransactionResult, except it is
     * marked as a non-reverted failure because of an invalid energy limit, and the output
     * byte-array is also set to empty.
     *
     * @param original The TransactionResult we were given.
     * @return The new Failed TransactionResult instance.
     */
    private static TransactionResult createCopyEnergyLimitExceeded(
            TransactionResult original, long energyUsed) {
        return new TransactionResult(
                TransactionStatus.rejection("Rejected: block energy limit exceeded"),
                original.logs,
                original.internalTransactions,
                energyUsed,
                ByteUtil.EMPTY_BYTE_ARRAY);
    }

    // TODO -- this has been marked as a temporary solution for a long time, someone should
    // investigate
    private static DataWord getDifficultyAsDataWord(byte[] diff) {
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return new DataWordImpl(diff);
    }
}
