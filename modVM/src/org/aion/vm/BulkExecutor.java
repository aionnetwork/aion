package org.aion.vm;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.ITxExecSummary;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.SideEffects;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;
import org.aion.fastvm.TransactionExecutor;

/**
 * One day this will actually be in the proper shape!
 *
 * <p>This will be the executor that takes in bulk transactions and invokes the vm's as needed.
 *
 * <p>Getting this into the proper shape will be done slowly..
 */
public class BulkExecutor {
    private static final Object LOCK = new Object();

    private AionTransaction transaction;
    private KernelTransactionContext context;
    private IAionBlock block;
    private KernelInterfaceForFastVM kernel;
    private boolean isLocalCall, allowNonceIncrement;
    private long blockRemainingNrg;
    private Logger logger;
    private PostExecutionWork postExecutionWork;

    public BulkExecutor(
            BlockDetails details,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            boolean isLocalCall,
            boolean allowNonceIncrement,
            long blockRemainingNrg,
            Logger logger,
            PostExecutionWork work) {

        this.kernel = new KernelInterfaceForFastVM(repo, allowNonceIncrement, isLocalCall);
        this.transaction = details.getTransactions().get(0);
        this.context = details.getExecutionContexts().get(0);
        this.block = details.getBlock();
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.blockRemainingNrg = blockRemainingNrg;
        this.logger = logger;
        this.postExecutionWork = work;
    }

    public List<AionTxExecSummary> execute() {
        synchronized (LOCK) {
            TransactionExecutor executor =
                    new TransactionExecutor(
                            this.transaction,
                            this.context,
                            this.block,
                            this.kernel,
                            logger,
                            blockRemainingNrg);
            TransactionResult result = executor.executeAndFetchResultOnly();

            KernelInterface kernelFromVM = result.getKernelInterface();

            // 1. Check the block energy limit & reject if necessary.
            if (computeEnergyUsed(this.transaction.getEnergyLimit(), result)
                    > this.blockRemainingNrg) {
                result.setResultCode(FastVmResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(0);
                result.setOutput(new byte[0]);
            }

            // 2. In the case of failure, nonce still increments and energy cost is still deducted.
            if (result.getResultCode().isFailed()) {
                BigInteger energyCost =
                        BigInteger.valueOf(
                                this.transaction.getEnergyLimit()
                                        * this.transaction.getEnergyPrice());

                KernelInterfaceForFastVM track = this.kernel.startTracking();
                track.incrementNonce(this.transaction.getSenderAddress());
                track.deductEnergyCost(this.transaction.getSenderAddress(), energyCost);
                track.flush();
            }

            // 3. build the tx summary && update the repo.
            AionTxExecSummary summary = buildSummaryAndUpdateRepository(kernelFromVM, result);

            // 4. do the execution-specific work.
            this.blockRemainingNrg -=
                    this.postExecutionWork.doExecutionWork(
                            this.kernel, summary, this.transaction, this.blockRemainingNrg);

            // 5. return the summary.
            return Collections.singletonList(summary);
        }
    }

    private AionTxExecSummary buildSummaryAndUpdateRepository(
            KernelInterface kernelFromVM, TransactionResult result) {
        SideEffects rootHelper = new SideEffects();
        if (result.getResultCode().isSuccess()) {
            rootHelper.merge(this.context.getSideEffects());
        } else {
            rootHelper.addInternalTransactions(
                    this.context.getSideEffects().getInternalTransactions());
        }

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(makeReceipt(rootHelper.getExecutionLogs(), result))
                        .logs(rootHelper.getExecutionLogs())
                        .deletedAccounts(rootHelper.getAddressesToBeDeleted())
                        .internalTransactions(rootHelper.getInternalTransactions())
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
                rootHelper.getAddressesToBeDeleted(),
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
            KernelInterfaceForFastVM track = this.kernel.startTracking();

            // Refund energy if transaction was successfully or reverted.
            if (result.getResultCode().isSuccess() || result.getResultCode().isRevert()) {
                track.adjustBalance(tx.getSenderAddress(), summary.getRefund());
            }

            tx.setNrgConsume(computeEnergyUsed(tx.getEnergyLimit(), result));

            // Pay the miner.
            track.adjustBalance(coinbase, summary.getFee());

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
        return limit - result.getEnergyRemaining();
    }
}
