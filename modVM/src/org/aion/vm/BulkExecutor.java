package org.aion.vm;

import static org.aion.mcf.valid.TransactionTypeRule.isValidAVMContractDeployment;

import java.util.ArrayList;
import java.util.List;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.SideEffects;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.tx.TxExecSummary;
import org.aion.kernel.AvmTransactionResult;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.mcf.vm.types.Log;
import org.aion.types.Address;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.VmFactoryImplementation.VM;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.vm.exception.VMException;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
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
 *
 * @implNote The repository and repositoryChild pairing given to the constructor of this class will
 *     be provided to the {@link PostExecutionWork} class to run the post-execution logic. If no
 *     top-level repository is required for the {@link PostExecutionWork} (that is, the repository
 *     field can safely be set null) then a second constructor with this field missing is provided,
 *     and only a repositoryChild is set. A repositoryChild is required for the actual
 *     BulkExecutor's logic, whereas repository is only used by the post-execution logic.
 *     <p>The {@code execute()} method is thread-safe.
 */
public class BulkExecutor {
    private static final Object LOCK = new Object();

    private static boolean avmEnabled = false;

    public static void enabledAvmCheck(boolean isEnabled) {
        avmEnabled = isEnabled;
    }

    private Repository repository;
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild;
    private PostExecutionWork postExecutionWork;
    private ExecutionBatch executionBatch;
    private Logger logger;
    private boolean isLocalCall;
    private boolean allowNonceIncrement;
    private long blockRemainingEnergy;
    private boolean fork040enable;

    /**
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * {@code executionBatch}.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * <p>ASSUMPTION: the parent of repositoryChild is repository.
     *
     * @param executionBatch The batch of transactions to execute.
     * @param repository The top-level repository.
     * @param repositoryChild The child of the top-level repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param fork040Enable the fork logic affect the fvm behavior.
     * @param logger The logger.
     * @param work The post-execution work to apply after each transaction is run.
     */
    public BulkExecutor(
            ExecutionBatch executionBatch,
            Repository repository,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040Enable,
            Logger logger,
            PostExecutionWork work) {

        this.executionBatch = executionBatch;
        this.repository = repository;
        this.repositoryChild = repositoryChild;
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.blockRemainingEnergy = blockRemainingEnergy;
        this.logger = logger;
        this.postExecutionWork = work;
        this.fork040enable = fork040Enable;
    }

    /**
     * Constructs a new bulk executor that will execute the transactions contained in the provided
     * {@code executionBatch}.
     *
     * <p>If {@code isLocalCall == true} then no state changes will be applied and no transaction
     * validation checks will be performed. Otherwise a transaction is run as normal.
     *
     * @param executionBatch The batch of transactions to execute.
     * @param repositoryChild The repository.
     * @param isLocalCall Whether or not the call is a network or local call.
     * @param allowNonceIncrement Whether or not to increment the sender's nonce.
     * @param blockRemainingEnergy The amount of energy remaining in the block.
     * @param logger The logger.
     * @param work The post-execution work to apply after each transaction is run.
     */
    public BulkExecutor(
            ExecutionBatch executionBatch,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            Logger logger,
            PostExecutionWork work) {

        this(
                executionBatch,
                repositoryChild,
                isLocalCall,
                allowNonceIncrement,
                blockRemainingEnergy,
                false,
                logger,
                work);
    }

    public BulkExecutor(
            ExecutionBatch executionBatch,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingEnergy,
            boolean fork040enable,
            Logger logger,
            PostExecutionWork work) {

        this(
                executionBatch,
                null,
                repositoryChild,
                isLocalCall,
                allowNonceIncrement,
                blockRemainingEnergy,
                fork040enable,
                logger,
                work);
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
                if (transactionIsForAionVirtualMachine(firstTransactionInNextBatch)) {
                    vmKernel =
                            new KernelInterfaceForAVM(
                                    this.repositoryChild.startTracking(),
                                    this.allowNonceIncrement,
                                    this.isLocalCall);
                    virtualMachineForNextBatch =
                            VirtualMachineProvider.getVirtualMachineInstance(VM.AVM, vmKernel);
                    nextBatchToExecute =
                            fetchNextBatchOfTransactionsForAionVirtualMachine(currentIndex);
                } else {
                    vmKernel =
                            new KernelInterfaceForFastVM(
                                    this.repositoryChild.startTracking(),
                                    this.allowNonceIncrement,
                                    this.isLocalCall,
                                    fork040enable);
                    virtualMachineForNextBatch =
                            VirtualMachineProvider.getVirtualMachineInstance(VM.FVM, vmKernel);
                    nextBatchToExecute =
                            fetchNextBatchOfTransactionsForFastVirtualMachine(currentIndex);
                }

                // Execute the next batch of transactions using the specified virtual machine.
                summaries.addAll(
                        executeTransactions(
                                virtualMachineForNextBatch, nextBatchToExecute, vmKernel));
                currentIndex += nextBatchToExecute.size();
            }

            return summaries;
        }
    }

    private List<AionTxExecSummary> executeTransactions(
            VirtualMachine virtualMachine, ExecutionBatch details, KernelInterface kernel)
            throws VMException {
        List<AionTxExecSummary> summaries = new ArrayList<>();

        // Run the transactions.
        SimpleFuture<TransactionResult>[] resultsAsFutures =
                virtualMachine.run(kernel, details.getExecutionContexts());

        // Process the results of the transactions.
        List<AionTransaction> transactions = details.getTransactions();
        TransactionContext[] contexts = details.getExecutionContexts();

        int length = resultsAsFutures.length;
        for (int i = 0; i < length; i++) {
            TransactionResult result = resultsAsFutures[i].get();

            if (result.getResultCode().isFatal()) {
                throw new VMException(result.toString());
            }

            KernelInterface kernelFromVM = result.getKernelInterface();

            AionTransaction transaction = transactions.get(i);
            TransactionContext context = contexts[i];

            // 1. Check the block energy limit & reject if necessary.
            long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
            if (energyUsed > this.blockRemainingEnergy) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);

                if (transactionIsForAionVirtualMachine(transaction)) {
                    ((AvmTransactionResult) result).setEnergyUsed(transaction.getEnergyLimit());
                } else {
                    result.setEnergyRemaining(0);
                }
            }

            // 2. build the transaction summary and update the repository (the one backing
            // this.kernel) with the contents of kernelFromVM accordingly.
            AionTxExecSummary summary =
                    buildSummaryAndUpdateRepository(transaction, context, kernelFromVM, result);

            // 3. Do any post execution work and update the remaining block energy.
            this.blockRemainingEnergy -=
                    this.postExecutionWork.doPostExecutionWork(
                            this.repository,
                            this.repositoryChild,
                            summary,
                            transaction,
                            this.blockRemainingEnergy);

            summaries.add(summary);
        }

        return summaries;
    }

    private AionTxExecSummary buildSummaryAndUpdateRepository(
            AionTransaction transaction,
            TransactionContext context,
            KernelInterface kernelFromVM,
            TransactionResult result) {

        // TODO: should Avm assure us that this is always non-null like the fvm does? But in Avm
        // TODO: a null return value is actually meaningful. Need to figure this out.
        if (result.getReturnData() == null) {
            result.setReturnData(ByteUtil.EMPTY_BYTE_ARRAY);
        }

        SideEffects sideEffects = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            sideEffects.merge(context.getSideEffects());
        } else {
            sideEffects.addInternalTransactions(context.getSideEffects().getInternalTransactions());
        }

        // We have to do this for now, because the kernel uses the log serialization, which is not
        // implemented in the Avm, and this type may become a POD type anyway..
        List<IExecutionLog> logs;
        if (transactionIsForAionVirtualMachine(transaction)) {
            logs = transferAvmLogsToKernel(sideEffects.getExecutionLogs());
        } else {
            logs = sideEffects.getExecutionLogs();
        }

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result))
                        .logs(logs)
                        .deletedAccounts(sideEffects.getAddressesToBeDeleted())
                        .internalTransactions(sideEffects.getInternalTransactions())
                        .result(result.getReturnData());

        ResultCode resultCode = result.getResultCode();

        if (transactionIsForAionVirtualMachine(transaction)) {
            kernelFromVM.commitTo(
                    new KernelInterfaceForAVM(
                            this.repositoryChild, this.allowNonceIncrement, this.isLocalCall));
        } else {
            kernelFromVM.commitTo(
                    new KernelInterfaceForFastVM(
                            this.repositoryChild,
                            this.allowNonceIncrement,
                            this.isLocalCall,
                            this.fork040enable));
        }

        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        updateRepository(
                summary,
                transaction,
                this.executionBatch.getBlock().getCoinbase(),
                sideEffects.getAddressesToBeDeleted(),
                result);

        return summary;
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

    private void updateRepository(
            TxExecSummary summary,
            AionTransaction tx,
            Address coinbase,
            List<Address> deleteAccounts,
            TransactionResult result) {

        if (!isLocalCall && !summary.isRejected()) {
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> track =
                    this.repositoryChild.startTracking();

            tx.setNrgConsume(computeEnergyUsed(tx.getEnergyLimit(), result));

            // Refund energy if transaction was successfully or reverted.
            if (!transactionIsForAionVirtualMachine(tx)) {
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
        }

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Transaction receipt: {}", summary.getReceipt());
            this.logger.debug("Transaction logs: {}", summary.getLogs());
        }
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
     * A transaction can only be for the avm if the avm is enabled. If it is not enabled this method
     * always returns false.
     *
     * <p>Otherwise, assuming the avm is enabled, a transaction is for the Avm if, and only if, one
     * of the following is true:
     *
     * <p>1. It is a CREATE transaction and its target VM is the AVM 2. It is a CALL transaction and
     * the destination is an AVM contract address 3. It is a CALL transaction and the destination is
     * not a contract address.
     */
    private boolean transactionIsForAionVirtualMachine(AionTransaction transaction) {
        // first verify that the AVM is enabled
        if (avmEnabled) {
            if (transaction.isContractCreationTransaction()) {
                return isValidAVMContractDeployment(transaction.getTargetVM());
            } else {
                Address destination = transaction.getDestinationAddress();
                return isValidAVMContractDeployment(repositoryChild.getVMUsed(destination))
                        || !isContractAddress(destination);
            }
        } else {
            return false;
        }
    }

    /** Returns true only if address is a contract. */
    private boolean isContractAddress(Address address) {
        byte[] code = this.repositoryChild.getCode(address);
        return (code != null) && (code.length > 0);
    }
}
