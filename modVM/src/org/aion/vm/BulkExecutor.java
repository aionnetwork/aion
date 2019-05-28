package org.aion.vm;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.mcf.valid.TransactionTypeRule.isValidAVMContractDeployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractFactory;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * The BulkExecutor receives a block of transactions.
 *
 * <p>The BulkExecutor will send the transactions off (in as large a contiguous bundle as possible)
 * to the appropriate {@link VirtualMachine} to be executed and will return the results of these
 * transactions to the caller.
 *
 * <p>The BulkExecutor makes the following promise to its caller:
 *
 * <p>The logical ordering of the transactions in the provided block will be
 * adhered to, so that it always appears as if the transaction at index 0 was executed first, then
 * the post-execution work is applied to it, then the transaction at index 1 following by the
 * post-execution work, and so on.
 */
public class BulkExecutor {
    private static final Object LOCK = new Object();
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository;
    private PostExecutionWork postExecutionWork;
    private IAionBlock block;
    private List<AionTransaction> transactions;
    private Logger logger;
    private boolean isLocalCall;
    private boolean allowNonceIncrement;
    private long blockRemainingEnergy;
    private boolean fork040enable;
    private boolean checkBlockEnergyLimit;

    private BulkExecutor(
            IAionBlock block,
            List<AionTransaction> transactions,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger,
            PostExecutionWork work) {

        this.block = block;
        this.transactions = transactions;
        this.repository = repository;
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.blockRemainingEnergy = blockRemainingEnergy;
        this.logger = logger;
        this.postExecutionWork = work;
        this.fork040enable = fork040Enable;
        this.checkBlockEnergyLimit = checkBlockEnergyLimit;
    }

    /**
     * Constructs a new bulk executor that will execute the specified transactions. These transactions
     * should constitute a subset of the transactions in the provided block.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param block The block containing the specified transactions.
     * @param transactions The transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     * @param logger The logger.
     * @param work The post-execution work to apply after each transaction is run.
     */
    public static BulkExecutor newExecutor(
        IAionBlock block,
        List<AionTransaction> transactions,
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
        boolean isLocalCall,
        boolean allowNonceIncrement,
        long blockRemainingEnergy,
        boolean fork040Enable,
        boolean checkBlockEnergyLimit,
        Logger logger,
        PostExecutionWork work) {

        if (work == null) {
            throw new NullPointerException("Cannot construct a BulkExecutor will null post-execution work!");
        }
        return new BulkExecutor(block, transactions, repository, isLocalCall, allowNonceIncrement, blockRemainingEnergy, fork040Enable, checkBlockEnergyLimit, logger, work);
    }

    /**
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * block.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param block The block of transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     * @param logger The logger.
     * @param work The post-execution work to apply after each transaction is run.
     */
    public static BulkExecutor newExecutorForBlock(
        IAionBlock block,
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
        boolean isLocalCall,
        boolean allowNonceIncrement,
        long blockRemainingEnergy,
        boolean fork040Enable,
        boolean checkBlockEnergyLimit,
        Logger logger,
        PostExecutionWork work) {

        if (block == null) {
            throw new NullPointerException("Cannot construct a BulkExecutor with null block!");
        }
        if (work == null) {
            throw new NullPointerException("Cannot construct a BulkExecutor will null post-execution work!");
        }
        return new BulkExecutor(block, block.getTransactionsList(), repository, isLocalCall, allowNonceIncrement, blockRemainingEnergy, fork040Enable, checkBlockEnergyLimit, logger, work);
    }

    /**
     * Constructs a new bulk executor that will execute the specified transactions. These transactions
     * should be a subset of the transactions in the provided block.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param block The block containing the specified transactions.
     * @param transactions The transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param logger The logger.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     */
    public static BulkExecutor newExecutorWithNoPostExecutionWork(
            IAionBlock block,
            List<AionTransaction> transactions,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger) {

        return new BulkExecutor(
                block,
                transactions,
                repository,
                isLocalCall,
                allowNonceIncrement,
                blockRemainingEnergy,
                fork040Enable,
                checkBlockEnergyLimit,
                logger,
                null);
    }

    /**
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * block.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param block The block of transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param logger The logger.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     */
    public static BulkExecutor newExecutorForBlockWithNoPostExecutionWork(
        IAionBlock block,
        RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
        boolean isLocalCall,
        boolean allowNonceIncrement,
        long blockRemainingEnergy,
        boolean fork040Enable,
        boolean checkBlockEnergyLimit,
        Logger logger) {

        return new BulkExecutor(
            block,
            block.getTransactionsList(),
            repository,
            isLocalCall,
            allowNonceIncrement,
            blockRemainingEnergy,
            fork040Enable,
            checkBlockEnergyLimit,
            logger,
            null);
    }

    public List<AionTxExecSummary> execute() throws VMException {
        //TODO: synchronization here is unnecessary -- determine whether to rm completely or what the proper scope is.
        //TODO: since repo is synchronized I suspect this can be removed entirely.
        synchronized (LOCK) {
            List<AionTxExecSummary> allSummaries = new ArrayList<>();

            int currentIndex = 0;
            while (currentIndex < this.transactions.size()) {
                List<AionTxExecSummary> currentBatchOfSummaries;
                AionTransaction firstTransactionInNextBatch = this.transactions.get(currentIndex);

                if (transactionIsForAionVirtualMachine(firstTransactionInNextBatch)) {
                    // Grab the next batch of avm transactions to execute.
                    List<AionTransaction> avmTransactionsToExecute = fetchNextBatchOfTransactionsForAionVirtualMachine(currentIndex);
                    AionTransaction[] avmTransactions = new AionTransaction[avmTransactionsToExecute.size()];
                    avmTransactionsToExecute.toArray(avmTransactions);

                    // Execute the avm transactions.
                    currentBatchOfSummaries = AvmTransactionExecutor.executeTransactions(
                        this.repository,
                        this.block,
                        avmTransactions,
                        this.postExecutionWork,
                        this.logger,
                        this.checkBlockEnergyLimit,
                        this.allowNonceIncrement,
                        this.isLocalCall,
                        this.blockRemainingEnergy);
                } else {
                    // Grab the next batch of fvm transactions to execute.
                    List<AionTransaction> fvmTransactionsToExecute = fetchNextBatchOfTransactionsForFastVirtualMachine(currentIndex);
                    AionTransaction[] fvmTransactions = new AionTransaction[fvmTransactionsToExecute.size()];
                    fvmTransactionsToExecute.toArray(fvmTransactions);

                    // Execute the fvm transactions.
                    currentBatchOfSummaries = FvmTransactionExecutor.executeTransactions(
                        this.repository,
                        this.block,
                        fvmTransactions,
                        this.postExecutionWork,
                        this.logger,
                        this.checkBlockEnergyLimit,
                        this.allowNonceIncrement,
                        this.isLocalCall,
                        this.fork040enable,
                        this.blockRemainingEnergy);
                }

                // Update the remaining energy left in the block.
                for (AionTxExecSummary currentSummary : currentBatchOfSummaries) {
                    if (!currentSummary.isRejected()) {
                        this.blockRemainingEnergy -= ((this.checkBlockEnergyLimit) ? currentSummary.getReceipt().getEnergyUsed() : 0);
                    }
                }

                // Add the current batch of summaries to the complete list and increment current index.
                allSummaries.addAll(currentBatchOfSummaries);
                currentIndex += currentBatchOfSummaries.size();
            }

            return allSummaries;
        }
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the FVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent FVM-bound transactions.
     */
    private List<AionTransaction> fetchNextBatchOfTransactionsForFastVirtualMachine(int startIndex) {
        for (int i = startIndex; i < this.transactions.size(); i++) {
            // Find the index of the next transaction that is not fvm-bound, that is where we stop.
            if (transactionIsForAionVirtualMachine(this.transactions.get(i))) {
                return this.transactions.subList(startIndex, i);
            }
        }

        return this.transactions.subList(startIndex, this.transactions.size());
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the AVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent AVM-bound transactions.
     */
    private List<AionTransaction> fetchNextBatchOfTransactionsForAionVirtualMachine(int startIndex) {
        for (int i = startIndex; i < this.transactions.size(); i++) {
            // Find the index of the next transaction that is not avm-bound, that is where we stop.
            if (!transactionIsForAionVirtualMachine(this.transactions.get(i))) {
                return this.transactions.subList(startIndex, i);
            }
        }

        return this.transactions.subList(startIndex, this.transactions.size());
    }

    /**
     * Otherwise, assuming the avm is enabled, a transaction is for the Avm if, and only if, one of
     * the following is true:
     *
     * <p>1. It is a CREATE transaction and its target VM is the AVM 2. It is a CALL transaction and
     * the destination is an AVM contract address 3. It is a CALL transaction and the destination is
     * not a contract address.
     */
    private boolean transactionIsForAionVirtualMachine(AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return isValidAVMContractDeployment(transaction.getTargetVM());
        } else {
            Address destination = transaction.getDestinationAddress();
            return !isContractAddress(destination) || isAllowedByAVM(destination);
        }
    }

    /** Returns true only if address is a contract. */
    private boolean isContractAddress(Address address) {
        if (ContractFactory.isPrecompiledContract(address)) {
            return true;
        } else {
            RepositoryCache cache = this.repository.startTracking();
            byte[] code = cache.getCode(address);
            // some contracts may have storage before they have code
            // TODO: need unit tests for both cases
            byte[] storage = ((AccountState) cache.getAccountState(address)).getStateRoot();
            return ((code != null) && (code.length > 0)
                    || (!Arrays.equals(storage, EMPTY_TRIE_HASH)));
        }
    }

    private boolean isAllowedByAVM(Address destination) {
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
