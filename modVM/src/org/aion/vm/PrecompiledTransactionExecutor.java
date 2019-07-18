package org.aion.vm;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.fastvm.SideEffects;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.tx.TxExecSummary;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.ContractExecutor;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;

public final class PrecompiledTransactionExecutor {

    public static List<AionTxExecSummary> executeTransactions(
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            long blockNumber,
            AionAddress blockCoinbase,
            AionTransaction[] transactions,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean decrementBlockEnergyLimit,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            long initialBlockEnergyLimit) {

        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();
        long blockRemainingEnergy = initialBlockEnergyLimit;

        IExternalStateForPrecompiled externalState =
                new ExternalStateForPrecompiled(
                        repository.startTracking(),
                        blockNumber,
                        isLocalCall,
                        allowNonceIncrement);

        for (AionTransaction transaction : transactions) {

            // Execute the contract.
            PrecompiledTransactionResult result = ContractExecutor.executeExternalCall(externalState, transaction);

            // Check the block energy limit & reject if necessary.
            long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
            if (energyUsed > blockRemainingEnergy) {
                result.setResultCode(PrecompiledResultCode.INVALID_NRG_LIMIT);
                result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
                result.setEnergyRemaining(0);
            }

            // Build the transaction summary.
            SideEffects sideEffects = new SideEffects();
            AionTxExecSummary summary = buildTransactionSummary(transaction, result, sideEffects);

            // If the transaction was not rejected, then commit the state changes.
            if (!result.getResultCode().isRejected()) {
                externalState.commit();
            }

            // For non-rejected non-local transactions, make some final repository updates.
            if (!isLocalCall && !summary.isRejected()) {
                RepositoryCache repositoryTracker = repository.startTracking();

                refundSender(repositoryTracker, summary, transaction, result);
                payMiner(repositoryTracker, blockCoinbase, summary);
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
        }

        return transactionSummaries;
    }

    private static void refundSender(
            RepositoryCache repository,
            TxExecSummary summary,
            AionTransaction transaction,
            PrecompiledTransactionResult result) {

        // Refund energy if transaction was successful or reverted.
        if (result.getResultCode().isSuccess() || result.getResultCode().isRevert()) {
            repository.addBalance(transaction.getSenderAddress(), summary.getRefund());
        }
    }

    private static void payMiner(
            RepositoryCache repository, AionAddress blockCoinbase, TxExecSummary summary) {
        repository.addBalance(blockCoinbase, summary.getFee());
    }

    private static void deleteAccountsMarkedForDeletion(
            RepositoryCache repository,
            SideEffects sideEffects,
            PrecompiledTransactionResult result) {
        if (result.getResultCode().isSuccess()) {
            for (AionAddress addr : sideEffects.getAddressesToBeDeleted()) {
                repository.deleteAccount(addr);
            }
        }
    }

    private static AionTxExecSummary buildTransactionSummary(
            AionTransaction transaction,
            PrecompiledTransactionResult result,
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
                        .logs(logs)
                        .deletedAccounts(transactionSideEffects.getAddressesToBeDeleted())
                        .internalTransactions(transactionSideEffects.getInternalTransactions())
                        .result(result.getReturnData());

        PrecompiledResultCode resultCode = result.getResultCode();

        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        return builder.build();
    }

    private static AionTxReceipt makeReceipt(
            AionTransaction transaction, List<Log> logs, PrecompiledTransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(computeEnergyUsed(transaction.getEnergyLimit(), result));
        receipt.setExecutionResult(result.getReturnData());
        receipt.setError(result.getResultCode().isSuccess() ? "" : result.getResultCode().name());

        return receipt;
    }

    private static long computeEnergyUsed(long limit, PrecompiledTransactionResult result) {
        return limit - result.getEnergyRemaining();
    }
}
