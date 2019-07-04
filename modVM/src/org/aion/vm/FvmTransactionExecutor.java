package org.aion.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.fastvm.SideEffects;
import org.aion.fastvm.SimpleFuture;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.tx.TxExecSummary;
import org.aion.mcf.types.IExecutionLog;
import org.aion.mcf.types.KernelInterface;
import org.aion.mcf.types.ResultCode;
import org.aion.mcf.vm.DataWord;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * A class that executes transactions that are to be run by the FVM.
 *
 * <p>This class is thread-safe.
 */
public final class FvmTransactionExecutor {

    /**
     * Executes the specified array of transactions using the FVM and returns a list of transaction
     * summaries, such that the i'th summary pertains to the i'th transaction in the input.
     *
     * <p>If no post-execution work is specified then none will be run. Otherwise, the
     * post-execution work will be applied in such a way that it appears, from the caller's
     * perspective, as if it was run immediately after each transaction sequentially.
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
     * @param fork040enabled Whether or not the 0.4.0 fork is enabled.
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
            boolean fork040enabled,
            long initialBlockEnergyLimit)
            throws VMException {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();

        long blockRemainingEnergy = initialBlockEnergyLimit;

        // Run the transactions.
        FastVirtualMachine fvm = new FastVirtualMachine();
        KernelInterface kernel =
                newKernelInterface(
                        repository.startTracking(),
                        block,
                        allowNonceIncrement,
                        isLocalCall,
                        fork040enabled);
        SimpleFuture<FastVmTransactionResult>[] resultsAsFutures = fvm.run(kernel, transactions);

        // Process the results of the transactions.
        int index = 0;
        for (SimpleFuture<FastVmTransactionResult> resultAsFuture : resultsAsFutures) {
            FastVmTransactionResult result = resultAsFuture.get();

            if (result.getResultCode().isFatal()) {
                throw new VMException(result.toString());
            }

            AionTransaction transaction = transactions[index];

            // Check the block energy limit & reject if necessary.
            long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
            if (energyUsed > blockRemainingEnergy) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
                result.setEnergyRemaining(0);
            }

            // Build the transaction summary.
            SideEffects sideEffects = new SideEffects();
            AionTxExecSummary summary = buildTransactionSummary(transaction, result, sideEffects);

            // Update the repository by committing any changes in the Fvm.
            KernelInterface kernelFromFvm = result.getKernelInterface();

            // Note that the vm may have returned SUCCESS (and so there may be state changes
            // here to
            // commit) but the block limit check turned this to REJECTED. Now we do not want the
            // changes.
            if (!result.getResultCode().isRejected()) {
                kernelFromFvm.commitTo(
                        newKernelInterface(
                                repository,
                                block,
                                allowNonceIncrement,
                                isLocalCall,
                                fork040enabled));
            }

            // For non-rejected non-local transactions, make some final repository updates.
            if (!isLocalCall && !summary.isRejected()) {
                RepositoryCache repositoryTracker = repository.startTracking();

                setEnergyConsumedAndRefundSender(repositoryTracker, summary, transaction, result);
                payMiner(repositoryTracker, block, summary);
                deleteAccountsMarkedForDeletion(repositoryTracker, sideEffects, result);

                repositoryTracker.flush();
            }

            // Do any post execution work.
            if (postExecutionWork != null) {
                postExecutionWork.doWork(repository, summary, transaction);
            }

            // Update the remaining block energy.
            if (!result.getResultCode().isRejected()) {
                blockRemainingEnergy -=
                        ((decrementBlockEnergyLimit) ? summary.getReceipt().getEnergyUsed() : 0);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Transaction receipt: {}", summary.getReceipt());
                logger.debug("Transaction logs: {}", summary.getLogs());
            }

            transactionSummaries.add(summary);
            index++;
        }

        return transactionSummaries;
    }

    private static AionTxExecSummary buildTransactionSummary(
            AionTransaction transaction,
            FastVmTransactionResult result,
            SideEffects transactionSideEffects) {
        if (result.getReturnData() == null) {
            result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
        }

        if (result.getResultCode().isSuccess()) {
            transactionSideEffects.addLogs(result.getLogs());
            transactionSideEffects.addInternalTransactions(result.getInternalTransactions());
            transactionSideEffects.addAllToDeletedAddresses(result.getDeletedAddresses());
        } else {
            transactionSideEffects.addInternalTransactions(result.getInternalTransactions());
        }

        // We have to do this for now, because the kernel uses the log serialization, which is not
        // implemented in the Avm, and this type may become a POD type anyway..
        List<Log> logs = transactionSideEffects.getExecutionLogs();

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
                        .logs(aionTypesToKernelLogs(logs))
                        .deletedAccounts(transactionSideEffects.getAddressesToBeDeleted())
                        .internalTransactions(transactionSideEffects.getInternalTransactions())
                        .result(result.getReturnData());

        ResultCode resultCode = result.getResultCode();

        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        return builder.build();
    }

    private static void setEnergyConsumedAndRefundSender(
            RepositoryCache repository,
            TxExecSummary summary,
            AionTransaction transaction,
            FastVmTransactionResult result) {
        transaction.setNrgConsume(computeEnergyUsed(transaction.getEnergyLimit(), result));

        // Refund energy if transaction was successfully or reverted.
        if (result.getResultCode().isSuccess() || result.getResultCode().isRevert()) {
            repository.addBalance(transaction.getSenderAddress(), summary.getRefund());
        }
    }

    private static void payMiner(
            RepositoryCache repository, IAionBlock block, TxExecSummary summary) {
        repository.addBalance(block.getCoinbase(), summary.getFee());
    }

    private static void deleteAccountsMarkedForDeletion(
            RepositoryCache repository, SideEffects sideEffects, FastVmTransactionResult result) {
        if (result.getResultCode().isSuccess()) {
            for (AionAddress addr : sideEffects.getAddressesToBeDeleted()) {
                repository.deleteAccount(addr);
            }
        }
    }

    private static AionTxReceipt makeReceipt(
            AionTransaction transaction, List<Log> logs, FastVmTransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(aionTypesToKernelLogs(logs));
        receipt.setNrgUsed(computeEnergyUsed(transaction.getEnergyLimit(), result));
        receipt.setExecutionResult(result.getReturnData());
        receipt.setError(result.getResultCode().isSuccess() ? "" : result.getResultCode().name());

        return receipt;
    }

    private static long computeEnergyUsed(long limit, FastVmTransactionResult result) {
        return limit - result.getEnergyRemaining();
    }

    // TODO -- this has been marked as a temporary solution for a long time, someone should
    // investigate
    private static DataWord getDifficultyAsDataWord(IAionBlock block) {
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return new DataWordImpl(diff);
    }

    private static KernelInterface newKernelInterface(
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            IAionBlock block,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            boolean fork040enable) {
        return new KernelInterfaceForFastVM(
                repository,
                allowNonceIncrement,
                isLocalCall,
                fork040enable,
                getDifficultyAsDataWord(block),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase());
    }

    private static List<IExecutionLog> aionTypesToKernelLogs(List<Log> aionTypesLogs) {
        List<IExecutionLog> kernelLogs = new ArrayList<>();
        for (Log aionTypesLog : aionTypesLogs) {
            kernelLogs.add(
                    new org.aion.mcf.vm.types.Log(
                            new AionAddress(aionTypesLog.copyOfAddress()),
                            aionTypesLog.copyOfTopics(),
                            aionTypesLog.copyOfData()));
        }
        return kernelLogs;
    }
}
