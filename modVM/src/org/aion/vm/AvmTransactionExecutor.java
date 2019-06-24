package org.aion.vm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.avm.core.AvmTransaction;
import org.aion.avm.core.FutureResult;
import org.aion.avm.core.types.InternalTransaction;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.vm.DataWord;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.SideEffects;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.Log;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.vm.api.interfaces.KernelInterface;
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
        KernelInterface kernel = newKernelInterface(repository.startTracking(), block, allowNonceIncrement, isLocalCall);

        try {
            // Acquire the avm lock and then run the transactions.
            AvmTransaction[] avmTransactions = convertKernelTransactionsToAvm(transactions);
            avm.acquireAvmLock();
            FutureResult[] resultsAsFutures = avm.run(kernel, avmTransactions);

            // Process the results of the transactions.
            int index = 0;
            for (FutureResult resultAsFuture : resultsAsFutures) {
                AvmTransactionResult result = resultAsFuture.get();

                if (result.getResultCode().isFatal()) {
                    throw new VMException(result.toString());
                }

                AionTransaction transaction = transactions[index];

                // Check the block energy limit & reject if necessary.
                long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
                if (energyUsed > blockRemainingEnergy) {
                    // TODO This needs to be changed to Invalid Energy Limit
                    result.setResultCode(AvmTransactionResult.Code.REJECTED);
                    result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
                    ((AvmTransactionResult) result).setEnergyUsed(transaction.getEnergyLimit());
                }

                // Build the transaction summary.
                AionTxExecSummary summary = buildTransactionSummary(transaction, result, isLocalCall);

                // Update the repository by committing any changes in the Avm.
                KernelInterface kernelFromAvm = result.getKernelInterface();
                kernelFromAvm.commitTo(newKernelInterface(repository, block, allowNonceIncrement, isLocalCall));

                // Do any post execution work if any is specified.
                if (postExecutionWork != null) {
                    postExecutionWork.doWork(repository, summary, transaction);
                }

                // Update the remaining block energy.
                if (!result.getResultCode().isRejected()) {
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

    private static AionTxExecSummary buildTransactionSummary(AionTransaction transaction, AvmTransactionResult result, boolean isLocalCall) {
        // TODO: should Avm assure us that this is always non-null like the fvm does? But in Avm
        // TODO: a null return value is actually meaningful. Need to figure this out.
        if (result.getReturnData() == null) {
            result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
        }

        SideEffects sideEffects = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            sideEffects.merge(result.getSideEffects());
        } else {
            sideEffects.addInternalTransactions(result.getSideEffects().getInternalTransactions());
        }

        List<IExecutionLog> logs = convertAvmLogsToKernel(sideEffects.getExecutionLogs());
        List<InternalTransactionInterface> internalTxs = convertAvmInternalTransactionToKernel(sideEffects.getInternalTransactions());
        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
            .logs(logs)
            .deletedAccounts(sideEffects.getAddressesToBeDeleted())
            .internalTransactions(internalTxs)
            .result(result.getReturnData());

        AvmTransactionResult.Code resultCode = result.getResultCode();
        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        if (!isLocalCall && !summary.isRejected()) {
            transaction.setNrgConsume(computeEnergyUsed(transaction.getEnergyLimit(), result));
        }

        return summary;
    }

    private static AionTxReceipt makeReceipt(AionTransaction transaction, List<IExecutionLog> logs, AvmTransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(computeEnergyUsed(transaction.getEnergyLimit(), result));
        receipt.setExecutionResult(result.getReturnData());
        receipt.setError(result.getResultCode().isSuccess() ? "" : result.getResultCode().name());
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
                            avmTx.getNonce(),
                            avmTx.getSenderAddress(),
                            avmTx.getDestinationAddress(),
                            avmTx.getValue(),
                            avmTx.getData(),
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
    private static AvmTransaction[] convertKernelTransactionsToAvm(AionTransaction[] aionTransactions) {
        AvmTransaction[] txs = new AvmTransaction[aionTransactions.length];
        for (int i = 0; i < aionTransactions.length; i++) {
            AionTransaction tx = aionTransactions[i];
            txs[i] = new AvmTransaction(
                            tx.getSenderAddress(),
                            tx.getDestinationAddress(),
                            tx.getTransactionHash(),
                            new BigInteger(1, tx.getValue()),
                            new BigInteger(1, tx.getNonce()),
                            tx.getEnergyPrice(),
                            tx.getEnergyLimit(),
                            tx.isContractCreationTransaction(),
                            tx.getData());
        }
        return txs;
    }

    private static long computeEnergyUsed(long limit, AvmTransactionResult result) {
        return limit - result.getEnergyRemaining();
    }

    // TODO -- this has been marked as a temporary solution for a long time, someone should investigate
    private static DataWord getDifficultyAsDataWord(IAionBlock block) {
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return new DataWordImpl(diff);
    }

    private static KernelInterface newKernelInterface(RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository, IAionBlock block, boolean allowNonceIncrement, boolean isLocalCall) {
        return new KernelInterfaceForAVM(
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
