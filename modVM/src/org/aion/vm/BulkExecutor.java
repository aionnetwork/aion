package org.aion.vm;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.mcf.valid.TransactionTypeRule.isValidAVMContractDeployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.types.Address;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * A class responsible for executing transactions in bulk (as a block), or optionally singly.
 *
 * If given a block, the BulkExecutor will send the transactions off (in as large a contiguous
 * bundle as possible) to the appropriate virtual machine to be executed and will return the results
 * of these transactions to the caller.
 *
 * This class is thread-safe.
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
     * @param block The block of transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param incrementSenderNonce Whether or not to increment the sender's nonce.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     * @param logger The logger.
     * @param postExecutionWork The post-execution work to apply after each transaction is run.
     */
    public static List<AionTxExecSummary> executeAllTransactionsInBlock(IAionBlock block, RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository, boolean isLocalCall, boolean incrementSenderNonce, boolean fork040Enable, boolean checkBlockEnergyLimit, Logger logger, PostExecutionWork postExecutionWork) throws VMException {
        if (block == null) {
            throw new NullPointerException("Cannot execute given a null block!");
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

        return executeInternal(block, block.getTransactionsList(), repository, postExecutionWork, logger, checkBlockEnergyLimit, incrementSenderNonce, isLocalCall, fork040Enable);
    }

    /**
     * Executes the specified transaction and returns the summary of executing this transaction.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param block The block that the specified transaction belongs to.
     * @param transaction The transaction to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param incrementSenderNonce Whether or not to increment the sender's nonce.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     * @param logger The logger.
     */
    public static AionTxExecSummary executeTransactionWithNoPostExecutionWork(IAionBlock block, AionTransaction transaction, RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository, boolean isLocalCall, boolean incrementSenderNonce, boolean fork040Enable, boolean checkBlockEnergyLimit, Logger logger) throws VMException {
        if (block == null) {
            throw new NullPointerException("Cannot execute given a null block!");
        }
        if (repository == null) {
            throw new NullPointerException("Cannot execute given a null repository!");
        }
        if (logger == null) {
            throw new NullPointerException("Cannot execute given a null logger!");
        }

        return executeInternal(block, Collections.singletonList(transaction), repository, null, logger, checkBlockEnergyLimit, incrementSenderNonce, isLocalCall, fork040Enable).get(0);
    }

    private static List<AionTxExecSummary> executeInternal(IAionBlock block, List<AionTransaction> transactions, RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository, PostExecutionWork postExecutionWork, Logger logger, boolean checkBlockEnergyLimit, boolean incrementSenderNonce, boolean isLocalCall, boolean fork040enabled) throws VMException {
        List<AionTxExecSummary> allSummaries = new ArrayList<>();

        long blockRemainingEnergy = block.getNrgLimit();

        int currentIndex = 0;
        while (currentIndex < transactions.size()) {
            List<AionTxExecSummary> currentBatchOfSummaries;
            AionTransaction firstTransactionInNextBatch = transactions.get(currentIndex);

            if (transactionIsForAionVirtualMachine(repository, firstTransactionInNextBatch)) {
                // Grab the next batch of avm transactions to execute.
                List<AionTransaction> avmTransactionsToExecute = fetchNextBatchOfTransactionsForAionVirtualMachine(repository, transactions, currentIndex);
                AionTransaction[] avmTransactions = new AionTransaction[avmTransactionsToExecute.size()];
                avmTransactionsToExecute.toArray(avmTransactions);

                // Execute the avm transactions.
                currentBatchOfSummaries = AvmTransactionExecutor.executeTransactions(
                    repository,
                    block,
                    avmTransactions,
                    postExecutionWork,
                    logger,
                    checkBlockEnergyLimit,
                    incrementSenderNonce,
                    isLocalCall,
                    blockRemainingEnergy);
            } else {
                // Grab the next batch of fvm transactions to execute.
                List<AionTransaction> fvmTransactionsToExecute = fetchNextBatchOfTransactionsForFastVirtualMachine(repository, transactions, currentIndex);
                AionTransaction[] fvmTransactions = new AionTransaction[fvmTransactionsToExecute.size()];
                fvmTransactionsToExecute.toArray(fvmTransactions);

                // Execute the fvm transactions.
                currentBatchOfSummaries = FvmTransactionExecutor.executeTransactions(
                    repository,
                    block,
                    fvmTransactions,
                    postExecutionWork,
                    logger,
                    checkBlockEnergyLimit,
                    incrementSenderNonce,
                    isLocalCall,
                    fork040enabled,
                    blockRemainingEnergy);
            }

            // Update the remaining energy left in the block.
            for (AionTxExecSummary currentSummary : currentBatchOfSummaries) {
                if (!currentSummary.isRejected()) {
                    blockRemainingEnergy -= ((checkBlockEnergyLimit) ? currentSummary.getReceipt().getEnergyUsed() : 0);
                }
            }

            // Add the current batch of summaries to the complete list and increment current index.
            allSummaries.addAll(currentBatchOfSummaries);
            currentIndex += currentBatchOfSummaries.size();
        }

        return allSummaries;
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the FVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent FVM-bound transactions.
     */
    private static List<AionTransaction> fetchNextBatchOfTransactionsForFastVirtualMachine(RepositoryCache repository, List<AionTransaction> transactions, int startIndex) {
        for (int i = startIndex; i < transactions.size(); i++) {
            // Find the index of the next transaction that is not fvm-bound, that is where we stop.
            if (transactionIsForAionVirtualMachine(repository, transactions.get(i))) {
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
    private static List<AionTransaction> fetchNextBatchOfTransactionsForAionVirtualMachine(RepositoryCache repository, List<AionTransaction> transactions, int startIndex) {
        for (int i = startIndex; i < transactions.size(); i++) {
            // Find the index of the next transaction that is not avm-bound, that is where we stop.
            if (!transactionIsForAionVirtualMachine(repository, transactions.get(i))) {
                return transactions.subList(startIndex, i);
            }
        }

        return transactions.subList(startIndex, transactions.size());
    }

    /**
     * Otherwise, assuming the avm is enabled, a transaction is for the Avm if, and only if, one of
     * the following is true:
     *
     * <p>1. It is a CREATE transaction and its target VM is the AVM 2. It is a CALL transaction and
     * the destination is an AVM contract address 3. It is a CALL transaction and the destination is
     * not a contract address.
     */
    private static boolean transactionIsForAionVirtualMachine(RepositoryCache repository, AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return isValidAVMContractDeployment(transaction.getTargetVM());
        } else {
            Address destination = transaction.getDestinationAddress();
            return !isContractAddress(repository, destination) || isAllowedByAVM(repository, destination);
        }
    }

    /** Returns true only if address is a contract. */
    private static boolean isContractAddress(RepositoryCache repository, Address address) {
        if (ContractFactory.isPrecompiledContract(address)) {
            return true;
        } else {
            RepositoryCache cache = repository.startTracking();
            byte[] code = cache.getCode(address);
            // some contracts may have storage before they have code
            // TODO: need unit tests for both cases
            byte[] storage = ((AccountState) cache.getAccountState(address)).getStateRoot();
            return ((code != null) && (code.length > 0)
                    || (!Arrays.equals(storage, EMPTY_TRIE_HASH)));
        }
    }

    private static boolean isAllowedByAVM(RepositoryCache repository, Address destination) {
        InternalVmType vm;
        if (ContractFactory.isPrecompiledContract(destination)) {
            // skip the call to disk
            vm = InternalVmType.FVM;
        } else {
            InternalVmType storedVmType = repository.getVMUsed(destination);

            // DEFAULT is returned when there was no contract information stored
            if (storedVmType == InternalVmType.UNKNOWN) {
                // will load contract into memory otherwise leading to consensus issues
                RepositoryCache track = repository.startTracking();
                vm = track.getVmType(destination);
            } else {
                vm = storedVmType;
            }
        }
        return vm != InternalVmType.FVM;
    }
}
