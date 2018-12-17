package org.aion.vm;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.ITxExecSummary;
import org.aion.fastvm.FastVirtualMachine;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.SideEffects;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.vm.api.interfaces.VirtualMachine;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;

/**
 * One day this will actually be in the proper shape!
 *
 * <p>This will be the executor that takes in bulk transactions and invokes the vm's as needed.
 *
 * <p>Getting this into the proper shape will be done slowly..
 */
public class BulkExecutor {
    private static final Object LOCK = new Object();

    private IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository;
    private PostExecutionWork postExecutionWork;
    private ExecutionBatch executionBlock;
    private Logger logger;
    private FastVirtualMachine fastVirtualMachine;
    private boolean isLocalCall, allowNonceIncrement;
    private long blockRemainingEnergy;

    public BulkExecutor(
            ExecutionBatch executionBlock,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingNrg,
            Logger logger,
            PostExecutionWork work) {

        this.executionBlock = executionBlock;
        this.repository = repo;
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.blockRemainingEnergy = blockRemainingNrg;
        this.logger = logger;
        this.postExecutionWork = work;

        // Create and initialize the FastVirtualMachine.
        KernelInterfaceForFastVM kernel = new KernelInterfaceForFastVM(this.repository, this.allowNonceIncrement, this.isLocalCall);
        this.fastVirtualMachine = new FastVirtualMachine();
        this.fastVirtualMachine.setKernelInterface(kernel);
    }

    public List<AionTxExecSummary> execute() {
        synchronized (LOCK) {
            List<AionTxExecSummary> summaries = new ArrayList<>();

            VirtualMachine virtualMachineForNextBatch = null;
            ExecutionBatch nextBatchToExecute = null;

            int currentIndex = 0;
            while (currentIndex < this.executionBlock.size()) {
                AionTransaction firstTransactionInNextBatch = this.executionBlock.getTransactions().get(currentIndex);

                if (transactionIsForFastVirtualMachine(firstTransactionInNextBatch)) {
                    virtualMachineForNextBatch = this.fastVirtualMachine;
                    nextBatchToExecute = fetchNextBatchOfTransactionsForFastVirtualMachine(currentIndex);
                } else {
                    // You never get here yet.
                }

                // Execute the next batch of transactions using the specified virtual machine.
                summaries.addAll(executeTransactions(virtualMachineForNextBatch, nextBatchToExecute));
                currentIndex += nextBatchToExecute.size();
            }

            return summaries;
        }
    }

    private List<AionTxExecSummary> executeTransactions(VirtualMachine virtualMachine, ExecutionBatch details) {
        List<AionTxExecSummary> summaries = new ArrayList<>();

        // Run the transactions.
        SimpleFuture<TransactionResult>[] resultsAsFutures = virtualMachine.run(details.getExecutionContexts());

        // Process the results of the transactions.
        List<AionTransaction> transactions = details.getTransactions();
        TransactionContext[] contexts = details.getExecutionContexts();

        int length = resultsAsFutures.length;
        for (int i = 0; i < length; i++) {
            TransactionResult result = resultsAsFutures[i].get();
            KernelInterface kernelFromVM = result.getKernelInterface();

            AionTransaction transaction = transactions.get(i);
            TransactionContext context = contexts[i];

            // 1. Check the block energy limit & reject if necessary.
            long energyUsed = computeEnergyUsed(transaction.getEnergyLimit(), result);
            if (energyUsed > this.blockRemainingEnergy) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(0);
                result.setOutput(new byte[0]);
            }

            // 2. build the transaction summary and update the repository (the one backing
            // this.kernel) with the contents of kernelFromVM accordingly.
            AionTxExecSummary summary =
                buildSummaryAndUpdateRepository(transaction, context, kernelFromVM, result);

            // 3. Do any post execution work and update the remaining block energy.
            this.blockRemainingEnergy -=
                this.postExecutionWork.doExecutionWork(
                    this.repository, summary, transaction, this.blockRemainingEnergy);

            summaries.add(summary);
        }

        return summaries;
    }

    private AionTxExecSummary buildSummaryAndUpdateRepository(
            AionTransaction transaction,
            TransactionContext context,
            KernelInterface kernelFromVM,
            TransactionResult result) {

        SideEffects sideEffects = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            sideEffects.merge(context.getSideEffects());
        } else {
            sideEffects.addInternalTransactions(context.getSideEffects().getInternalTransactions());
        }

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(
                                makeReceipt(transaction, sideEffects.getExecutionLogs(), result))
                        .logs(sideEffects.getExecutionLogs())
                        .deletedAccounts(sideEffects.getAddressesToBeDeleted())
                        .internalTransactions(sideEffects.getInternalTransactions())
                        .result(result.getOutput());

        ResultCode resultCode = result.getResultCode();

        if (resultCode.isSuccess()) {
            kernelFromVM.flush();
        } else if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        updateRepository(
                summary,
                transaction,
                this.executionBlock.getBlock().getCoinbase(),
                sideEffects.getAddressesToBeDeleted(),
                result);

        return summary;
    }

    private AionTxReceipt makeReceipt(
            AionTransaction transaction, List<IExecutionLog> logs, TransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(computeEnergyUsed(transaction.getEnergyLimit(), result));
        receipt.setExecutionResult(result.getOutput());
        receipt.setError(result.getResultCode().isSuccess() ? "" : result.getResultCode().name());

        return receipt;
    }

    private void updateRepository(
            ITxExecSummary summary,
            AionTransaction tx,
            Address coinbase,
            List<Address> deleteAccounts,
            TransactionResult result) {

        if (!isLocalCall && !summary.isRejected()) {
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track =
                    this.repository.startTracking();

            // Refund energy if transaction was successfully or reverted.
            if (result.getResultCode().isSuccess() || result.getResultCode().isRevert()) {
                track.addBalance(tx.getSenderAddress(), summary.getRefund());
            }

            tx.setNrgConsume(computeEnergyUsed(tx.getEnergyLimit(), result));

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

    private long computeEnergyUsed(long limit, TransactionResult result) {
        System.out.println("---> " + (limit - result.getEnergyRemaining()));
        return limit - result.getEnergyRemaining();
    }

    private ExecutionBatch fetchNextBatchOfTransactionsForFastVirtualMachine(int startIndex) {
        int endIndexExclusive = this.executionBlock.size();

        // Find the index of the next transaction that is not fvm-bound.
        List<AionTransaction> transactions = this.executionBlock.getTransactions();
        for (int i = startIndex; i < endIndexExclusive; i++) {
            if (!transactionIsForFastVirtualMachine(transactions.get(i))) {
                endIndexExclusive = i;
            }
        }

        return this.executionBlock.slice(startIndex, endIndexExclusive);
    }

    private boolean transactionIsForFastVirtualMachine(AionTransaction transaction) {
        //TODO: to eventually be replaced with real logic.
        return true;
    }

}
