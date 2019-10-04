package org.aion.zero.impl.vm.fvm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.zero.impl.vm.common.PostExecutionWork;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.fastvm.FvmDataWord;
import org.aion.fastvm.FvmWrappedTransactionResult;
import org.aion.fastvm.IExternalStateForFvm;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;
import org.aion.util.bytes.ByteUtil;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
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
     * @param fork040enabled Whether or not the 0.4.0 fork is enabled.
     * @param initialBlockEnergyLimit The initial block energy limit at the time of running these
     *     transactions.
     * @return a list of transaction summaries pertaining to the transactions.
     */
    public static List<AionTxExecSummary> executeTransactions(
            RepositoryCache<AccountState> repository,
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
            boolean fork040enabled,
            long initialBlockEnergyLimit,
            boolean unityForkEnabled)
            throws VmFatalException {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();

        long blockRemainingEnergy = initialBlockEnergyLimit;

        // Run the transactions.
        IExternalStateForFvm externalState =
                new ExternalStateForFvm(
                        repository.startTracking(),
                        blockCoinbase,
                        getDifficultyAsDataWord(blockDifficulty),
                        isLocalCall,
                        allowNonceIncrement,
                        fork040enabled,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        unityForkEnabled);

        // Process the results of the transactions.
        for (AionTransaction transaction : transactions) {
            FvmWrappedTransactionResult wrappedResult =
                    FastVirtualMachine.run(externalState, new ExternalCapabilitiesForFvm(), toAionTypesTransaction(transaction), fork040enabled);

            TransactionResult result = wrappedResult.result;
            List<AionAddress> deletedAddresses = wrappedResult.deletedAddresses;

            if (result.transactionStatus.isFatal()) {
                throw new VmFatalException(result.toString());
            }

            // Check the block energy limit & reject if necessary.
            if (result.energyUsed > blockRemainingEnergy) {
                TransactionStatus status = TransactionStatus.rejection("Invalid Energy Limit");
                result = new TransactionResult(status, result.logs, result.internalTransactions, 0, ByteUtil.EMPTY_BYTE_ARRAY);
            }

            // Build the transaction summary.
            AionTxExecSummary summary = buildTransactionSummary(transaction, result, deletedAddresses);

            // If the transaction was not rejected, then commit the state changes.
            if (!result.transactionStatus.isRejected()) {
                externalState.commit();
            }

            // For non-rejected non-local transactions, make some final repository updates.
            if (!isLocalCall && !summary.isRejected()) {
                RepositoryCache repositoryTracker = repository.startTracking();

                refundSender(repositoryTracker, summary, transaction, result);
                payMiner(repositoryTracker, blockCoinbase, summary);
                deleteAccountsMarkedForDeletion(repositoryTracker, summary.getDeletedAccounts(), result);

                repositoryTracker.flush();
            }

            // Do any post execution work.
            if (postExecutionWork != null) {
                postExecutionWork.doWork(repository, summary, transaction);
            }

            // Update the remaining block energy.
            if (!result.transactionStatus.isRejected() && decrementBlockEnergyLimit) {
                blockRemainingEnergy -= summary.getReceipt().getEnergyUsed();
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Transaction receipt: {}", summary.getReceipt());
                logger.debug("Transaction logs: {}", summary.getLogs());
            }

            transactionSummaries.add(summary);
        }

        return transactionSummaries;
    }

    private static AionTxExecSummary buildTransactionSummary(
            AionTransaction transaction,
            TransactionResult result,
            List<AionAddress> deletedAddresses) {

        boolean success = result.transactionStatus.isSuccess();
        List<Log> logs = success ? result.logs : new ArrayList<>();
        List<AionAddress> addressesToBeDeleted = success ? deletedAddresses : new ArrayList<>();

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
                        .logs(logs)
                        .deletedAccounts(addressesToBeDeleted)
                        .internalTransactions(result.internalTransactions)
                        .result(result.copyOfTransactionOutput().orElse(ByteUtil.EMPTY_BYTE_ARRAY));

        if (result.transactionStatus.isRejected()) {
            builder.markAsRejected();
        } else if (result.transactionStatus.isFailed()) {
            builder.markAsFailed();
        }

        return builder.build();
    }

    private static void refundSender(
            RepositoryCache repository,
            AionTxExecSummary summary,
            AionTransaction transaction,
            TransactionResult result) {

        // Refund energy if transaction was successful or reverted.
        if (result.transactionStatus.isSuccess() || result.transactionStatus.isReverted()) {
            repository.addBalance(transaction.getSenderAddress(), summary.getRefund());
        }
    }

    private static void payMiner(
            RepositoryCache repository, AionAddress miner, AionTxExecSummary summary) {
        repository.addBalance(miner, summary.getFee());
    }

    private static void deleteAccountsMarkedForDeletion(
            RepositoryCache repository, List<AionAddress> addressesToBeDeleted, TransactionResult result) {
        if (result.transactionStatus.isSuccess()) {
            for (AionAddress addr : addressesToBeDeleted) {
                repository.deleteAccount(addr);
            }
        }
    }

    private static AionTxReceipt makeReceipt(
            AionTransaction transaction, List<Log> logs, TransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(result.energyUsed);
        receipt.setExecutionResult(result.copyOfTransactionOutput().orElse(ByteUtil.EMPTY_BYTE_ARRAY));
        receipt.setError(result.transactionStatus.isSuccess() ? "" : result.transactionStatus.causeOfError);

        return receipt;
    }

    // TODO -- this has been marked as a temporary solution for a long time, someone should
    // investigate
    private static FvmDataWord getDifficultyAsDataWord(byte[] diff) {
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return FvmDataWord.fromBytes(diff);
    }

    private static Transaction toAionTypesTransaction(AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return Transaction.contractCreateTransaction(transaction.getSenderAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        } else {
            return Transaction.contractCallTransaction(transaction.getSenderAddress(), transaction.getDestinationAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        }
    }
}
