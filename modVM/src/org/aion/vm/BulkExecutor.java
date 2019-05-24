package org.aion.vm;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.mcf.valid.TransactionTypeRule.isValidAVMContractDeployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.SideEffects;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.tx.Transaction;
import org.aion.interfaces.tx.TxExecSummary;
import org.aion.interfaces.vm.DataWord;
import org.aion.kernel.AvmTransactionResult;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.types.Address;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * The BulkExecutor receives a batch of transactions with the following assumption: all transactions
 * in the provided {@link ExecutionBatch} belong to the same block.
 *
 * <p>The BulkExecutor will send the transactions off (in as large a contiguous bundle as possible)
 * to the appropriate {@link VirtualMachine} to be executed and will return the results of these
 * transactions to the caller.
 *
 * <p>The BulkExecutor makes the following promise to its caller:
 *
 * <p>The logical ordering of the transactions in the provided {@link ExecutionBatch} will be
 * adhered to, so that it always appears as if the transaction at index 0 was executed first, then
 * the post-execution work is applied to it, then the transaction at index 1 following by the
 * post-execution work, and so on.
 */
public class BulkExecutor {
    private static final Object LOCK = new Object();
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository;
    private PostExecutionWork postExecutionWork;
    private ExecutionBatch executionBatch;
    private Logger logger;
    private boolean isLocalCall;
    private boolean allowNonceIncrement;
    private long blockRemainingEnergy;
    private boolean fork040enable;
    private boolean checkBlockEnergyLimit;

    private BulkExecutor(
            ExecutionBatch executionBatch,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger,
            PostExecutionWork work) {

        this.executionBatch = executionBatch;
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
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * {@code executionBatch}.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param executionBatch The batch of transactions to execute.
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
        ExecutionBatch executionBatch,
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
        return new BulkExecutor(executionBatch, repository, isLocalCall, allowNonceIncrement, blockRemainingEnergy, fork040Enable, checkBlockEnergyLimit, logger, work);
    }

    /**
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * {@code executionBatch}.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param executionBatch The batch of transactions to execute.
     * @param repository The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param logger The logger.
     * @param checkBlockEnergyLimit Whether or not to check the block energy limit overflow per transaction.
     */
    public static BulkExecutor newExecutorWithNoPostExecutionWork(
            ExecutionBatch executionBatch,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040Enable,
            boolean checkBlockEnergyLimit,
            Logger logger) {

        return new BulkExecutor(
                executionBatch,
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
        synchronized (LOCK) {
            List<AionTxExecSummary> summaries = new ArrayList<>();

            VirtualMachine virtualMachineForNextBatch;
            ExecutionBatch nextBatchToExecute;

            int currentIndex = 0;
            while (currentIndex < this.executionBatch.size()) {
                AionTransaction firstTransactionInNextBatch =
                        this.executionBatch.getTransactions().get(currentIndex);

                KernelInterface vmKernel;
                IAionBlock block = executionBatch.getBlock();

                if (transactionIsForAionVirtualMachine(firstTransactionInNextBatch)) {
                    vmKernel =
                            new KernelInterfaceForAVM(
                                    this.repository.startTracking(),
                                    this.allowNonceIncrement,
                                    this.isLocalCall,
                                    getDifficultyAsDataWord(block),
                                    block.getNumber(),
                                    block.getTimestamp(),
                                    block.getNrgLimit(),
                                    block.getCoinbase());
                    nextBatchToExecute =
                            fetchNextBatchOfTransactionsForAionVirtualMachine(currentIndex);

                    summaries.addAll(executeTransactionsUsingAvm(nextBatchToExecute, vmKernel));
                    currentIndex += nextBatchToExecute.size();
                } else {
                    vmKernel =
                            new KernelInterfaceForFastVM(
                                    this.repository.startTracking(),
                                    this.allowNonceIncrement,
                                    this.isLocalCall,
                                    fork040enable,
                                    getDifficultyAsDataWord(block),
                                    block.getNumber(),
                                    block.getTimestamp(),
                                    block.getNrgLimit(),
                                    block.getCoinbase());

                    virtualMachineForNextBatch = new FastVirtualMachine();
                    nextBatchToExecute =
                            fetchNextBatchOfTransactionsForFastVirtualMachine(currentIndex);

                    summaries.addAll(
                        executeTransactionsUsingFvm(
                            virtualMachineForNextBatch, nextBatchToExecute, vmKernel));
                    currentIndex += nextBatchToExecute.size();
                }
            }

            return summaries;
        }
    }

    private List<AionTxExecSummary> executeTransactionsUsingAvm(ExecutionBatch details, KernelInterface kernel) throws VMException {
        List<AionTxExecSummary> summaries = new ArrayList<>();

        AionVirtualMachine virtualMachine = LongLivedAvm.singleton();

        Transaction[] txArray = new Transaction[details.size()];

        // Acquire the avm lock and then run the transactions.
        try {
            virtualMachine.acquireAvmLock();
            SimpleFuture<TransactionResult>[] resultsAsFutures = virtualMachine.run(kernel, details.getTransactions().toArray(txArray));

            // Process the results of the transactions.
            List<AionTransaction> transactions = details.getTransactions();

            int length = resultsAsFutures.length;
            for (int i = 0; i < length; i++) {
                TransactionResult result = resultsAsFutures[i].get();

                if (result.getResultCode().isFatal()) {
                    throw new VMException(result.toString());
                }

                KernelInterface kernelFromVM = result.getKernelInterface();

                AionTransaction transaction = transactions.get(i);

                // 1. Check the block energy limit & reject if necessary.
                long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
                if (energyUsed > this.blockRemainingEnergy) {
                    result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                    result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
                    ((AvmTransactionResult) result).setEnergyUsed(transaction.getEnergyLimit());
                }

                // 2. build the transaction summary and update the repository (the one backing
                // this kernel) with the contents of kernelFromVM accordingly.
                AionTxExecSummary summary = buildSummaryAndUpdateRepositoryForAvmTransaction(transaction, kernelFromVM, result);

                // 3. Do any post execution work.
                if (this.postExecutionWork != null) {
                    this.postExecutionWork.doWork(
                        this.repository,
                        summary,
                        transaction);
                }

                // 4.  Update the remaining block energy.
                if (!result.getResultCode().isRejected()) {
                    this.blockRemainingEnergy -= ((this.checkBlockEnergyLimit) ? summary.getReceipt().getEnergyUsed() : 0);
                }

                summaries.add(summary);
            }
        } catch (Throwable t) {
            throw t;
        } finally {
            virtualMachine.releaseAvmLock();
        }

        return summaries;
    }

    private List<AionTxExecSummary> executeTransactionsUsingFvm(VirtualMachine virtualMachine, ExecutionBatch details, KernelInterface kernel) throws VMException {
        List<AionTxExecSummary> summaries = new ArrayList<>();

        // Run the transactions.
        Transaction[] txArray = new Transaction[details.size()];
        SimpleFuture<TransactionResult>[] resultsAsFutures = virtualMachine.run(kernel, details.getTransactions().toArray(txArray));

        // Process the results of the transactions.
        List<AionTransaction> transactions = details.getTransactions();

        int length = resultsAsFutures.length;
        for (int i = 0; i < length; i++) {
            TransactionResult result = resultsAsFutures[i].get();

            if (result.getResultCode().isFatal()) {
                throw new VMException(result.toString());
            }

            KernelInterface kernelFromVM = result.getKernelInterface();

            AionTransaction transaction = transactions.get(i);

            // 1. Check the block energy limit & reject if necessary.
            long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
            if (energyUsed > this.blockRemainingEnergy) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
                result.setEnergyRemaining(0);
            }

            // 2. build the transaction summary and update the repository (the one backing
            // this kernel) with the contents of kernelFromVM accordingly.
            AionTxExecSummary summary = buildSummaryAndUpdateRepositoryForFvmTransaction(transaction, kernelFromVM, result);

            // 3. Do any post execution work.
            if (this.postExecutionWork != null) {
                this.postExecutionWork.doWork(
                    this.repository,
                    summary,
                    transaction);
            }

            // 4.  Update the remaining block energy.
            if (!result.getResultCode().isRejected()) {
                this.blockRemainingEnergy -= ((this.checkBlockEnergyLimit) ? summary.getReceipt().getEnergyUsed() : 0);
            }

            summaries.add(summary);
        }

        return summaries;
    }

    private AionTxExecSummary buildSummaryAndUpdateRepositoryForAvmTransaction(AionTransaction transaction, KernelInterface kernelFromVM, TransactionResult result) {
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

        // We have to do this for now, because the kernel uses the log serialization, which is not
        // implemented in the Avm, and this type may become a POD type anyway..
        List<IExecutionLog> logs = transferAvmLogsToKernel(sideEffects.getExecutionLogs());

        AionTxExecSummary.Builder builder =
            AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
                .logs(logs)
                .deletedAccounts(sideEffects.getAddressesToBeDeleted())
                .internalTransactions(sideEffects.getInternalTransactions())
                .result(result.getReturnData());

        ResultCode resultCode = result.getResultCode();
        IAionBlock block = executionBatch.getBlock();

        kernelFromVM.commitTo(
            new KernelInterfaceForAVM(
                this.repository,
                this.allowNonceIncrement,
                this.isLocalCall,
                getDifficultyAsDataWord(block),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase()));

        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        updateRepositoryForAvm(summary, transaction, result);

        return summary;
    }

    private AionTxExecSummary buildSummaryAndUpdateRepositoryForFvmTransaction(AionTransaction transaction, KernelInterface kernelFromVM, TransactionResult result) {
        SideEffects sideEffects = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            sideEffects.merge(result.getSideEffects());
        } else {
            sideEffects.addInternalTransactions(result.getSideEffects().getInternalTransactions());
        }

        // We have to do this for now, because the kernel uses the log serialization, which is not
        // implemented in the Avm, and this type may become a POD type anyway..
        List<IExecutionLog> logs = sideEffects.getExecutionLogs();

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
            .logs(logs)
            .deletedAccounts(sideEffects.getAddressesToBeDeleted())
            .internalTransactions(sideEffects.getInternalTransactions())
            .result(result.getReturnData());

        ResultCode resultCode = result.getResultCode();
        IAionBlock block = executionBatch.getBlock();

        kernelFromVM.commitTo(
            new KernelInterfaceForFastVM(
                this.repository,
                this.allowNonceIncrement,
                this.isLocalCall,
                this.fork040enable,
                getDifficultyAsDataWord(block),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase()));

        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        updateRepositoryForFvm(
            summary,
            transaction,
            this.executionBatch.getBlock().getCoinbase(),
            sideEffects.getAddressesToBeDeleted(),
            result);

        return summary;
    }

    private void updateRepositoryForAvm(TxExecSummary summary, AionTransaction tx, TransactionResult result) {
        if (!isLocalCall && !summary.isRejected()) {
            tx.setNrgConsume(computeEnergyUsed(tx.getEnergyLimit(), result));
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Transaction receipt: {}", summary.getReceipt());
            this.logger.debug("Transaction logs: {}", summary.getLogs());
        }
    }

    private void updateRepositoryForFvm(TxExecSummary summary, AionTransaction tx, Address coinbase, List<Address> deleteAccounts, TransactionResult result) {
        if (!isLocalCall && !summary.isRejected()) {
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> track = this.repository.startTracking();

            tx.setNrgConsume(computeEnergyUsed(tx.getEnergyLimit(), result));

            // Refund energy if transaction was successfully or reverted.
            if (result.getResultCode().isSuccess() || result.getResultCode().isRevert()) {
                track.addBalance(tx.getSenderAddress(), summary.getRefund());
            }

            // Pay the miner.
            track.addBalance(coinbase, summary.getFee());

            // Delete any accounts marked for deletion.
            if (result.getResultCode().isSuccess()) {
                for (Address addr : deleteAccounts) {
                    track.deleteAccount(addr);
                }
            }
            track.flush();
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Transaction receipt: {}", summary.getReceipt());
            this.logger.debug("Transaction logs: {}", summary.getLogs());
        }
    }

    private static List<IExecutionLog> transferAvmLogsToKernel(List<IExecutionLog> avmLogs) {
        List<IExecutionLog> logs = new ArrayList<>();
        for (IExecutionLog avmLog : avmLogs) {
            logs.add(new Log(avmLog.getSourceAddress(), avmLog.getTopics(), avmLog.getData()));
        }
        return logs;
    }

    private static AionTxReceipt makeReceipt(
        AionTransaction transaction, List<IExecutionLog> logs, TransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(computeEnergyUsed(transaction.getEnergyLimit(), result));
        receipt.setExecutionResult(result.getReturnData());
        receipt.setError(result.getResultCode().isSuccess() ? "" : result.getResultCode().name());

        return receipt;
    }

    private static long computeEnergyUsed(long limit, TransactionResult result) {
        return limit - result.getEnergyRemaining();
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the FVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent FVM-bound transactions.
     */
    private ExecutionBatch fetchNextBatchOfTransactionsForFastVirtualMachine(int startIndex) {
        List<AionTransaction> transactions = this.executionBatch.getTransactions();

        for (int i = startIndex; i < this.executionBatch.size(); i++) {
            // Find the index of the next transaction that is not fvm-bound, that is where we stop.
            if (transactionIsForAionVirtualMachine(transactions.get(i))) {
                return this.executionBatch.slice(startIndex, i);
            }
        }

        return this.executionBatch.slice(startIndex, this.executionBatch.size());
    }

    /**
     * Returns a batch of transactions to execute that are destined to be executed by the AVM,
     * starting with the transaction at index {@code startIndex} (inclusive) up to and including all
     * subsequent AVM-bound transactions.
     */
    private ExecutionBatch fetchNextBatchOfTransactionsForAionVirtualMachine(int startIndex) {
        List<AionTransaction> transactions = this.executionBatch.getTransactions();

        for (int i = startIndex; i < this.executionBatch.size(); i++) {
            // Find the index of the next transaction that is not avm-bound, that is where we stop.
            if (!transactionIsForAionVirtualMachine(transactions.get(i))) {
                return this.executionBatch.slice(startIndex, i);
            }
        }

        return this.executionBatch.slice(startIndex, this.executionBatch.size());
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

    private DataWord getDifficultyAsDataWord(IAionBlock block) {
        // TODO: temp solution for difficulty length
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        return new DataWordImpl(diff);
    }
}
