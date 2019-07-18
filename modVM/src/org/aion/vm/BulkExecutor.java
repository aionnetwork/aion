package org.aion.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.ExecutionType;
import org.aion.base.AionTransaction;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTxExecSummary;
import org.slf4j.Logger;

/**
 * A class responsible for executing transactions in bulk (as a block), or optionally singly.
 *
 * <p>If given a block, the BulkExecutor will send the transactions off (in as large a contiguous
 * bundle as possible) to the appropriate virtual machine to be executed and will return the results
 * of these transactions to the caller.
 *
 * <p>This class is thread-safe.
 */
public final class BulkExecutor {

    /**
     * Executes all of the transactions in the specified block and returns a list of summaries such
     * that the i'th summary corresponds to the i'th transaction in the block. The transactions will
     * be executed so that, from the perspective of the caller, the order of execution is the same
     * as the order of the transactions in the block, and the specified post-execution work is
     * applied immediately after each transaction executes, for all transactions.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param blockDifficulty The current best block's difficulty.
     * @param blockNumber The current best block number.
     * @param blockTimestamp The current best block timestamp.
     * @param blockNrgLimit The current best block energy limit.
     * @param blockCoinbase The address of the miner.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param incrementSenderNonce Whether or not to increment the sender's nonce.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per
     *     transaction.
     * @param logger The logger.
     * @param postExecutionWork The post-execution work to apply after each transaction is run.
     * @param blockCachingContext indicates to the AVM the purpose for the transaction execution
     *     (AVM specific parameter)
     * @param cachedBlockNumber represents a main chain block that is common to the current main
     *     chain and the block that is about to be imported used for cache retrieval (AVM specific
     *     parameter)
     */
    public static List<AionTxExecSummary> executeAllTransactionsInBlock(
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            List<AionTransaction> transactions,
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            boolean isLocalCall,
            boolean incrementSenderNonce,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger,
            PostExecutionWork postExecutionWork,
            BlockCachingContext blockCachingContext,
            long cachedBlockNumber)
            throws VMException {

        if (blockDifficulty == null) {
            throw new NullPointerException("Cannot execute given a null block difficulty!");
        }
        if (blockCoinbase == null) {
            throw new NullPointerException("Cannot execute given a null block coinbase!");
        }
        if (transactions == null) {
            throw new NullPointerException("Cannot execute given a null transactions!");
        }
        if (repository == null) {
            throw new NullPointerException("Cannot execute given a null repository!");
        }
        if (logger == null) {
            throw new NullPointerException("Cannot execute given a null logger!");
        }
        if (postExecutionWork == null) {
            throw new NullPointerException("Cannot execute given a null postExecutionWork!");
        }

        return executeInternal(
                blockDifficulty,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockCoinbase,
                transactions,
                repository,
                postExecutionWork,
                logger,
                checkBlockEnergyLimit,
                incrementSenderNonce,
                isLocalCall,
                fork040Enable,
                blockCachingContext,
                cachedBlockNumber);
    }

    /**
     * Executes the specified transaction and returns the summary of executing this transaction.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param blockDifficulty The current best block's difficulty.
     * @param blockNumber The current best block number.
     * @param blockTimestamp The current best block timestamp.
     * @param blockNrgLimit The current best block energy limit.
     * @param blockCoinbase The address of the miner.
     * @param transaction The transaction to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param incrementSenderNonce Whether or not to increment the sender's nonce.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per
     *     transaction.
     * @param logger The logger.
     * @param blockCachingContext indicates to the AVM the purpose for the transaction execution
     *     (AVM specific parameter)
     */
    public static AionTxExecSummary executeTransactionWithNoPostExecutionWork(
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            AionTransaction transaction,
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            boolean isLocalCall,
            boolean incrementSenderNonce,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger,
            BlockCachingContext blockCachingContext,
            long cachedBlockNumber)
            throws VMException {

        if (blockDifficulty == null) {
            throw new NullPointerException("Cannot execute given a null block difficulty!");
        }
        if (blockCoinbase == null) {
            throw new NullPointerException("Cannot execute given a null block coinbase!");
        }
        if (repository == null) {
            throw new NullPointerException("Cannot execute given a null repository!");
        }
        if (logger == null) {
            throw new NullPointerException("Cannot execute given a null logger!");
        }

        return executeInternal(
                        blockDifficulty,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockCoinbase,
                        Collections.singletonList(transaction),
                        repository,
                        null,
                        logger,
                        checkBlockEnergyLimit,
                        incrementSenderNonce,
                        isLocalCall,
                        fork040Enable,
                        blockCachingContext,
                        cachedBlockNumber)
                .get(0);
    }

    /** This is the common execution point that all publicly-exposed execute methods call into. */
    private static List<AionTxExecSummary> executeInternal(
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            List<AionTransaction> transactions,
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean checkBlockEnergyLimit,
            boolean incrementSenderNonce,
            boolean isLocalCall,
            boolean fork040enabled,
            BlockCachingContext blockCachingContext,
            long cachedBlockNumber)
            throws VMException {
        List<AionTxExecSummary> allSummaries = new ArrayList<>();

        long blockRemainingEnergy = blockNrgLimit;

        int currentIndex = 0;
        while (currentIndex < transactions.size()) {
            List<AionTxExecSummary> currentBatchOfSummaries;
            AionTransaction firstTransactionInNextBatch = transactions.get(currentIndex);

            if (transactionIsForAionVirtualMachine(repository, firstTransactionInNextBatch)) {
                currentBatchOfSummaries =
                        executeNextBatchOfAvmTransactions(
                                repository,
                                transactions,
                                currentIndex,
                                blockDifficulty,
                                blockNumber,
                                blockTimestamp,
                                blockNrgLimit,
                                blockCoinbase,
                                postExecutionWork,
                                logger,
                                checkBlockEnergyLimit,
                                incrementSenderNonce,
                                isLocalCall,
                                blockRemainingEnergy,
                                blockCachingContext.avmType,
                                cachedBlockNumber);
            } else if (transactionIsForFastVirtualMachine(
                    repository, firstTransactionInNextBatch)) {
                currentBatchOfSummaries =
                        executeNextBatchOfFvmTransactions(
                                repository,
                                transactions,
                                currentIndex,
                                blockDifficulty,
                                blockNumber,
                                blockTimestamp,
                                blockNrgLimit,
                                blockCoinbase,
                                postExecutionWork,
                                logger,
                                checkBlockEnergyLimit,
                                incrementSenderNonce,
                                isLocalCall,
                                blockRemainingEnergy,
                                fork040enabled);
            } else if (transactionIsPrecompiledContractCall(firstTransactionInNextBatch)) {
                currentBatchOfSummaries =
                        executeNextBatchOfPrecompiledTransactions(
                                repository,
                                transactions,
                                currentIndex,
                                blockNumber,
                                blockCoinbase,
                                postExecutionWork,
                                logger,
                                checkBlockEnergyLimit,
                                incrementSenderNonce,
                                isLocalCall,
                                blockRemainingEnergy);
            } else {
                throw new IllegalStateException(
                        "Transaction is not destined for any known VM: "
                                + firstTransactionInNextBatch);
            }

            // Update the remaining energy left in the block.
            for (AionTxExecSummary currentSummary : currentBatchOfSummaries) {
                if (!currentSummary.isRejected()) {
                    blockRemainingEnergy -=
                            ((checkBlockEnergyLimit)
                                    ? currentSummary.getReceipt().getEnergyUsed()
                                    : 0);
                }
            }

            // Add the current batch of summaries to the complete list and increment current index.
            allSummaries.addAll(currentBatchOfSummaries);
            currentIndex += currentBatchOfSummaries.size();
        }

        return allSummaries;
    }

    /**
     * Returns the execution summaries of the next batch of avm transactions to be run. This batch
     * of transactions begins with the transaction at index {@code currentIndex} in the provided
     * list of transactions and includes all subsequent transactions that are avm-bound.
     */
    private static List<AionTxExecSummary> executeNextBatchOfAvmTransactions(
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            List<AionTransaction> transactions,
            int currentIndex,
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean checkBlockEnergyLimit,
            boolean incrementSenderNonce,
            boolean isLocalCall,
            long blockRemainingEnergy,
            ExecutionType executionType,
            long cachedBlockNumber)
            throws VMException {

        // Grab the next batch of avm transactions to execute.
        List<AionTransaction> avmTransactionsToExecute =
                fetchNextBatchOfTransactionsForAionVirtualMachine(
                        repository, transactions, currentIndex);
        AionTransaction[] avmTransactions = new AionTransaction[avmTransactionsToExecute.size()];
        avmTransactionsToExecute.toArray(avmTransactions);

        // Execute the avm transactions.
        return AvmTransactionExecutor.executeTransactions(
                repository,
                blockDifficulty,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockCoinbase,
                avmTransactions,
                postExecutionWork,
                logger,
                checkBlockEnergyLimit,
                incrementSenderNonce,
                isLocalCall,
                blockRemainingEnergy,
                executionType,
                cachedBlockNumber);
    }

    /**
     * Returns the execution summaries of the next batch of fvm transactions to be run. This batch
     * of transactions begins with the transaction at index {@code currentIndex} in the provided
     * list of transactions and includes all subsequent transactions that are fvm-bound.
     */
    private static List<AionTxExecSummary> executeNextBatchOfFvmTransactions(
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            List<AionTransaction> transactions,
            int currentIndex,
            byte[] blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean checkBlockEnergyLimit,
            boolean incrementSenderNonce,
            boolean isLocalCall,
            long blockRemainingEnergy,
            boolean fork040enabled)
            throws VMException {

        // Grab the next batch of fvm transactions to execute.
        List<AionTransaction> fvmTransactionsToExecute =
                fetchNextBatchOfTransactionsForFastVirtualMachine(
                        repository, transactions, currentIndex);
        AionTransaction[] fvmTransactions = new AionTransaction[fvmTransactionsToExecute.size()];
        fvmTransactionsToExecute.toArray(fvmTransactions);

        // Execute the fvm transactions.
        return FvmTransactionExecutor.executeTransactions(
                repository,
                blockDifficulty,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockCoinbase,
                fvmTransactions,
                postExecutionWork,
                logger,
                checkBlockEnergyLimit,
                incrementSenderNonce,
                isLocalCall,
                fork040enabled,
                blockRemainingEnergy);
    }

    /**
     * Returns the execution summaries of the next batch of precompiled contract call transactions
     * to be run. This batch of transactions begins with the transaction at index {@code
     * currentIndex} in the provided list of transactions and includes all subsequent transactions
     * that are precompiled-bound.
     */
    private static List<AionTxExecSummary> executeNextBatchOfPrecompiledTransactions(
            RepositoryCache<AccountState, IBlockStoreBase> repository,
            List<AionTransaction> transactions,
            int currentIndex,
            long blockNumber,
            AionAddress blockCoinbase,
            PostExecutionWork postExecutionWork,
            Logger logger,
            boolean checkBlockEnergyLimit,
            boolean incrementSenderNonce,
            boolean isLocalCall,
            long blockRemainingEnergy) {

        // Grab the next batch of precompiled contract call transactions to execute.
        List<AionTransaction> precompiledTransactionsToExecute =
                fetchNextBatchOfPrecompiledContractCallTransactions(transactions, currentIndex);
        AionTransaction[] precompiledTransactions =
                new AionTransaction[precompiledTransactionsToExecute.size()];
        precompiledTransactionsToExecute.toArray(precompiledTransactions);

        // Execute the precompiled contract call transactions.
        return PrecompiledTransactionExecutor.executeTransactions(
                repository,
                blockNumber,
                blockCoinbase,
                precompiledTransactions,
                postExecutionWork,
                logger,
                checkBlockEnergyLimit,
                incrementSenderNonce,
                isLocalCall,
                blockRemainingEnergy);
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the FVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent FVM-bound transactions.
     */
    private static List<AionTransaction> fetchNextBatchOfTransactionsForFastVirtualMachine(
            RepositoryCache repository, List<AionTransaction> transactions, int startIndex) {
        for (int i = startIndex; i < transactions.size(); i++) {
            // Find the index of the next transaction that is not fvm-bound, that is where we stop.
            if (!transactionIsForFastVirtualMachine(repository, transactions.get(i))) {
                return transactions.subList(startIndex, i);
            }
        }

        return transactions.subList(startIndex, transactions.size());
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the AVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent AVM-bound transactions.
     */
    private static List<AionTransaction> fetchNextBatchOfTransactionsForAionVirtualMachine(
            RepositoryCache repository, List<AionTransaction> transactions, int startIndex) {
        for (int i = startIndex; i < transactions.size(); i++) {
            // Find the index of the next transaction that is not avm-bound, that is where we stop.
            if (!transactionIsForAionVirtualMachine(repository, transactions.get(i))) {
                return transactions.subList(startIndex, i);
            }
        }

        return transactions.subList(startIndex, transactions.size());
    }

    /**
     * Returns a batch of transactions to execute that are precompiled contract calls, starting with
     * the transaction at index {@code startIndex} (inclusive) up to and including all subsequent
     * precompiled contract call transactions.
     */
    private static List<AionTransaction> fetchNextBatchOfPrecompiledContractCallTransactions(
            List<AionTransaction> transactions, int startIndex) {
        for (int i = startIndex; i < transactions.size(); i++) {
            // Find the index of the next transaction that is not a precompiled contract call, that
            // is where we stop.
            if (!transactionIsPrecompiledContractCall(transactions.get(i))) {
                return transactions.subList(startIndex, i);
            }
        }

        return transactions.subList(startIndex, transactions.size());
    }

    /**
     * Returns true only if the specified transaction is destined to be executed by the AVM.
     * Otherwise false.
     *
     * <p>A transaction is for the Avm if, and only if, the avm is enabled and one of the following
     * is true:
     *
     * <p>1. It is a CREATE transaction and its target VM is the AVM
     *
     * <p>2. It is a CALL transaction and the destination is an AVM contract address
     *
     * <p>3. It is a CALL transaction and the destination is not a contract address.
     */
    private static boolean transactionIsForAionVirtualMachine(
            RepositoryCache repository, AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return TransactionTypeRule.isValidAVMContractDeployment(transaction.getType());
        } else {
            AionAddress destination = transaction.getDestinationAddress();
            return destinationIsAvmContract(repository, destination)
                    || destinationIsRegularAccount(repository, destination);
        }
    }

    /**
     * Returns true only if the specified transaction is destined to be executed by the FVM.
     * Otherwise false.
     *
     * <p>A transaction is for the Fvm if, and only if, one of the following is true:
     *
     * <p>1. It is a CREATE transaction and its target VM is the FVM
     *
     * <p>2. It is a CALL transaction and the destination is a FVM contract address.
     *
     * @param repository The repository.
     * @param transaction The transaction in question.
     * @return whether the transaction is for the FVM.
     */
    private static boolean transactionIsForFastVirtualMachine(
            RepositoryCache repository, AionTransaction transaction) {

        if (transaction.isContractCreationTransaction()) {
            // CREATE can only be for avm or fvm, since fvm isn't defined as precisely as avm by any
            // of our helpers, we consider it not avm.
            // TODO: we should have a valid helper for isValidFVMContractDeployment()
            return !TransactionTypeRule.isValidAVMContractDeployment(transaction.getType());
        } else {
            AionAddress destination = transaction.getDestinationAddress();
            return destinationIsFvmContract(repository, destination);
        }
    }

    /**
     * Returns true only if the specified transaction is destined to be executed as a precompiled
     * contract call. Otherwise false.
     *
     * <p>A transaction is a precompiled contract call if, and only if, it is <b>not</b> a CREATE
     * transaction <b>and</b> the destination address is a precompiled contract address.
     *
     * @param transaction The transaction in question.
     * @return whether the transaction is a precompiled contract call.
     */
    private static boolean transactionIsPrecompiledContractCall(AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return false;
        } else {
            return destinationIsPrecompiledContract(transaction.getDestinationAddress());
        }
    }

    /** Returns the InternalVmType recorded for the given address. */
    private static InternalVmType getInternalVmType(
            RepositoryCache repository, AionAddress destination) {
        // will load contract into memory otherwise leading to consensus issues
        RepositoryCache<AccountState, IBlockStoreBase> track = repository.startTracking();
        AccountState accountState = track.getAccountState(destination);

        InternalVmType vm;
        if (accountState == null) {
            // the address doesn't exist yet, so it can be used by either vm
            vm = InternalVmType.EITHER;
        } else {
            vm = repository.getVMUsed(destination, accountState.getCodeHash());

            // UNKNOWN is returned when there was no contract information stored
            if (vm == InternalVmType.UNKNOWN) {
                // use the in-memory value
                vm = track.getVmType(destination);
            }
        }
        return vm;
    }

    /** Returns true only if the given destination address is an Avm contract address. */
    private static boolean destinationIsAvmContract(
            RepositoryCache repository, AionAddress destination) {
        return getInternalVmType(repository, destination) == InternalVmType.AVM;
    }

    /**
     * Returns true only if the given destination address is a regular account (ie. it is not an
     * Avm, Fvm or Precompiled contract address).
     */
    private static boolean destinationIsRegularAccount(
            RepositoryCache repository, AionAddress destination) {
        return !(getInternalVmType(repository, destination).isContract());
    }

    /** Returns true only if the given destination address is a Fvm contract address. */
    private static boolean destinationIsFvmContract(
            RepositoryCache repository, AionAddress destination) {
        return !ContractInfo.isPrecompiledContract(destination)
                && (getInternalVmType(repository, destination) == InternalVmType.FVM);
    }

    /** Returns true only if the given destination address is a precompiled contract address. */
    private static boolean destinationIsPrecompiledContract(AionAddress destination) {
        return ContractInfo.isPrecompiledContract(destination);
    }
}
