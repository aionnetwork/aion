package org.aion.vm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.avm.core.FutureResult;
import org.aion.avm.core.IExternalState;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.vm.DataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.Log;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.*;
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
     * @param block The block in which the transactions are included.
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
     * @return a list of transaction summaries pertaining to the transactions.
     */
    public static List<AionTxExecSummary> executeTransactions(
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            IAionBlock block,
            AionTransaction[] transactions,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean decrementBlockEnergyLimit,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            long initialBlockEnergyLimit)
            throws VMException {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();

        long blockRemainingEnergy = initialBlockEnergyLimit;

        AionVirtualMachine avm = LongLivedAvm.singleton();
        IExternalState kernel = newExternalState(repository.startTracking(), block, allowNonceIncrement, isLocalCall);

        try {
            // Acquire the avm lock and then run the transactions.
            Transaction[] avmTransactions = convertKernelTransactionsToAvm(transactions);
            avm.acquireAvmLock();
            FutureResult[] resultsAsFutures = avm.run(kernel, avmTransactions);

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
                AionTxExecSummary summary = buildTransactionSummary(transaction, result, isLocalCall);

                // Update the repository by committing any changes in the Avm.
                IExternalState externalState = resultAsFuture.getExternalState();
                externalState.commitTo(
                    newExternalState(repository, block, allowNonceIncrement, isLocalCall));

                // Do any post execution work if any is specified.
                if (postExecutionWork != null) {
                    postExecutionWork.doWork(repository, summary, transaction);
                }

                // Update the remaining block energy.
                if (!result.transactionStatus.isRejected()) {
                    blockRemainingEnergy -= ((decrementBlockEnergyLimit) ? summary.getReceipt().getEnergyUsed() : 0);
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

    private static AionTxExecSummary buildTransactionSummary(AionTransaction transaction, TransactionResult result, boolean isLocalCall) {
        List<IExecutionLog> logs = result.transactionStatus.isSuccess() ? convertAvmLogsToKernel(result.logs) : new ArrayList<>();
        List<InternalTransactionInterface> internalTxs = convertAvmInternalTransactionToKernel(result.internalTransactions);
        byte[] output = result.copyOfTransactionOutput().orElse(ByteUtil.EMPTY_BYTE_ARRAY);

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result, output))
            .logs(logs)
            .deletedAccounts(new ArrayList<>())
            .internalTransactions(internalTxs)
            .result(output);

        TransactionStatus resultCode = result.transactionStatus;
        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        if (!isLocalCall && !summary.isRejected()) {
            transaction.setNrgConsume(result.energyUsed);
        }

        return summary;
    }

    private static AionTxReceipt makeReceipt(AionTransaction transaction, List<IExecutionLog> logs, TransactionResult result, byte[] output) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(result.energyUsed);
        receipt.setExecutionResult(output);
        receipt.setError(result.transactionStatus.causeOfError);
        return receipt;
    }

    /**
     * Converts the AVM log implementation into a kernel log implementation, since the kernel
     * implementation has features that the kernel relies on that are not implemented by the AVM
     * version.
     *
     * <p>This should be a temporary measure until both are using the exact same type.
     *
     * @param avmLogs The AVM logs.
     * @return The equivalent kernel logs.
     */
    private static List<IExecutionLog> convertAvmLogsToKernel(List<org.aion.types.Log> avmLogs) {
        List<IExecutionLog> logs = new ArrayList<>();
        for (org.aion.types.Log avmLog : avmLogs) {
            logs.add(new Log(new AionAddress(avmLog.copyOfAddress()), avmLog.copyOfTopics(), avmLog.copyOfData()));
        }
        return logs;
    }

    /**
     * Converts the AVM Internal Transaction implementation into a kernel Internal Transaction
     * implementation, since the kernel implementation has features that the kernel relies on that
     * are not implemented by the AVM version.
     *
     * <p>This should be a temporary measure until both are using the exact same type.
     *
     * @param avmInternalTransactions The AVM Internal Transactions.
     * @return The equivalent kernel Internal Transactions.
     */
    private static List<InternalTransactionInterface> convertAvmInternalTransactionToKernel(List<InternalTransaction> avmInternalTransactions) {
        List<InternalTransactionInterface> txs = new ArrayList<>();
        for (InternalTransaction avmTx : avmInternalTransactions) {
            txs.add(new AionInternalTx(
                            null,
                            0,
                            0,
                            avmTx.senderNonce.toByteArray(),
                            avmTx.sender,
                            avmTx.destination,
                            avmTx.value.toByteArray(),
                            avmTx.copyOfData(),
                            null));
        }
        return txs;
    }

    /**
     * Converts the Kernel AionTransaction implementation into an AVM Transaction implementation.
     *
     * <p>This should be a temporary measure until both are using the exact same type.
     *
     * @param aionTransactions The Kernel Transactions.
     * @return The equivalent AVM Transactions.
     */
    private static Transaction[] convertKernelTransactionsToAvm(AionTransaction[] aionTransactions) {
        Transaction[] txs = new Transaction[aionTransactions.length];
        for (int i = 0; i < aionTransactions.length; i++) {
            AionTransaction tx = aionTransactions[i];
            if (tx.isContractCreationTransaction()) {
                txs[i] = Transaction.contractCreateTransaction(
                            tx.getSenderAddress(),
                            tx.getTransactionHash(),
                            new BigInteger(1, tx.getNonce()),
                            new BigInteger(1, tx.getValue()),
                            tx.getData(),
                            tx.getEnergyLimit(),
                            tx.getEnergyPrice());
            } else {
                txs[i] = Transaction.contractCallTransaction(
                            tx.getSenderAddress(),
                            tx.getDestinationAddress(),
                            tx.getTransactionHash(),
                            new BigInteger(1, tx.getNonce()),
                            new BigInteger(1, tx.getValue()),
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
    private static TransactionResult createCopyEnergyLimitExceeded(TransactionResult original, long energyUsed) {
        return new TransactionResult(
                TransactionStatus.rejection("Rejected: block energy limit exceeded"),
                original.logs,
                original.internalTransactions,
                energyUsed,
                ByteUtil.EMPTY_BYTE_ARRAY);
    }

    // TODO -- this has been marked as a temporary solution for a long time, someone should investigate
    private static DataWord getDifficultyAsDataWord(IAionBlock block) {
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return new DataWordImpl(diff);
    }

    private static IExternalState newExternalState(RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository, IAionBlock block, boolean allowNonceIncrement, boolean isLocalCall) {
        return new ExternalStateForAvm(
            repository,
            allowNonceIncrement,
            isLocalCall,
            getDifficultyAsDataWord(block),
            block.getNumber(),
            block.getTimestamp(),
            block.getNrgLimit(),
            block.getCoinbase());
    }
}