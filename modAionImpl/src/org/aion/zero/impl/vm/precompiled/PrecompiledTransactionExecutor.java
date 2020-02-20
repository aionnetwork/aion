package org.aion.zero.impl.vm.precompiled;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.zero.impl.vm.common.PostExecutionWork;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.mcf.db.RepositoryCache;
import org.aion.precompiled.type.ContractExecutor;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledWrappedTransactionResult;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;
import org.aion.util.bytes.ByteUtil;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.slf4j.Logger;

public final class PrecompiledTransactionExecutor {

    public static List<AionTxExecSummary> executeTransactions(
            RepositoryCache<AccountState> repository,
            long blockNumber,
            AionAddress blockCoinbase,
            AionTransaction[] transactions,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean decrementBlockEnergyLimit,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            boolean fork032Enabled,
            long initialBlockEnergyLimit) {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();
        long blockRemainingEnergy = initialBlockEnergyLimit;

        IExternalStateForPrecompiled externalState =
                new ExternalStateForPrecompiled(
                        repository,
                        blockNumber,
                        isLocalCall,
                        fork032Enabled,
                        allowNonceIncrement);

        for (AionTransaction transaction : transactions) {

            // Execute the contract.
            PrecompiledWrappedTransactionResult wrappedResult = ContractExecutor.executeExternalCall(new ExternalCapabilitiesForPrecompiled(), externalState, toAionTypesTransaction(transaction));

            TransactionResult result = wrappedResult.result;
            List<AionAddress> deletedAddresses = wrappedResult.deletedAddresses;

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

                repositoryTracker.flushTo(repository, true);
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
            RepositoryCache repository, AionAddress blockCoinbase, AionTxExecSummary summary) {
        repository.addBalance(blockCoinbase, summary.getFee());
    }

    private static void deleteAccountsMarkedForDeletion(
            RepositoryCache repository,
            List<AionAddress> addressesToBeDeleted,
            TransactionResult result) {
        if (result.transactionStatus.isSuccess()) {
            for (AionAddress addr : addressesToBeDeleted) {
                repository.deleteAccount(addr);
            }
        }
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

    private static Transaction toAionTypesTransaction(AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return Transaction.contractCreateTransaction(transaction.getSenderAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        } else {
            return Transaction.contractCallTransaction(transaction.getSenderAddress(), transaction.getDestinationAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        }
    }
}
