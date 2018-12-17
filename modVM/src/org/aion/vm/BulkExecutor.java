package org.aion.vm;

import java.util.Collections;
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
import org.aion.zero.types.IAionBlock;
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
    private AionTransaction transaction;
    private KernelTransactionContext context;
    private IAionBlock block;
    private Logger logger;
    private boolean isLocalCall, allowNonceIncrement;
    private long blockRemainingEnergy;

    public BulkExecutor(
            BlockDetails details,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingNrg,
            Logger logger,
            PostExecutionWork work) {

        this.repository = repo;
        this.transaction = details.getTransactions().get(0);
        this.context = details.getExecutionContexts().get(0);
        this.block = details.getBlock();
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.blockRemainingEnergy = blockRemainingNrg;
        this.logger = logger;
        this.postExecutionWork = work;
    }

    public List<AionTxExecSummary> execute() {
        synchronized (LOCK) {
            KernelInterfaceForFastVM kernel =
                    new KernelInterfaceForFastVM(
                            this.repository, this.allowNonceIncrement, this.isLocalCall);

            VirtualMachine fvm = new FastVirtualMachine();
            fvm.setKernelInterface(kernel);

            TransactionContext[] contexts = new TransactionContext[] { this.context };
            SimpleFuture<TransactionResult>[] results = fvm.run(contexts);
            TransactionResult result = results[0].get();

            KernelInterface kernelFromVM = result.getKernelInterface();

            // 1. Check the block energy limit & reject if necessary.
            if (computeEnergyUsed(this.transaction.getEnergyLimit(), result)
                    > this.blockRemainingEnergy) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(0);
                result.setOutput(new byte[0]);
            }

            // 2. build the transaction summary and update the repository (the one backing
            // this.kernel) with the contents of kernelFromVM accordingly.
            AionTxExecSummary summary = buildSummaryAndUpdateRepository(kernelFromVM, result);

            // 2. Do any post execution work and update the remaining block energy.
            this.blockRemainingEnergy -=
                    this.postExecutionWork.doExecutionWork(
                            this.repository, summary, this.transaction, this.blockRemainingEnergy);

            // 5. return the summary.
            return Collections.singletonList(summary);
        }
    }

    private AionTxExecSummary buildSummaryAndUpdateRepository(
            KernelInterface kernelFromVM, TransactionResult result) {

        SideEffects sideEffects = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            sideEffects.merge(this.context.getSideEffects());
        } else {
            sideEffects.addInternalTransactions(
                    this.context.getSideEffects().getInternalTransactions());
        }

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(sideEffects.getExecutionLogs(), result))
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
                this.transaction,
                this.block.getCoinbase(),
                sideEffects.getAddressesToBeDeleted(),
                result);

        return summary;
    }

    private AionTxReceipt makeReceipt(List<IExecutionLog> logs, TransactionResult result) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(this.transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(computeEnergyUsed(this.transaction.getEnergyLimit(), result));
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
}
