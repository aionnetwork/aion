/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.vm;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

/**
 * Transaction executor is the middle man between kernel and VM. It executes transactions and yields
 * transaction receipts.
 *
 * @author yulong
 */
public class TransactionExecutor extends AbstractExecutor {
    // provider is essential to execute, but it is only set manually with a setter, not good
    // practice
    private TransactionContext ctx;
    private AionTransaction tx;
    private IAionBlock block;
    private ExecutorProvider provider;

    /**
     * Create a new transaction executor. <br>
     * <br>
     * IMPORTANT: be sure to accumulate nrg used in a block outside the transaction executor
     *
     * @param tx transaction to be executed
     * @param block a temporary block used to garner relevant environmental variables
     */
    public TransactionExecutor(
            AionTransaction tx,
            IAionBlock block,
            IRepository repo,
            boolean isLocalCall,
            long blockRemainingNrg,
            Logger logger) {

        super(repo, isLocalCall, blockRemainingNrg, logger);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing transaction: {}", tx);
        }

        this.tx = tx;
        this.block = block;

        /*
         * transaction info
         */
        byte[] txHash = tx.getTransactionHash();
        Address address =
                tx.isContractCreationTransaction()
                        ? tx.getContractAddress()
                        : tx.getDestinationAddress();
        Address origin = tx.getSenderAddress();
        Address caller = tx.getSenderAddress();

        /*
         * nrg info
         */
        DataWord nrgPrice = tx.nrgPrice();
        long nrgLimit = tx.nrgLimit() - tx.transactionCost(block.getNumber());
        DataWord callValue = new DataWord(ArrayUtils.nullToEmpty(tx.getValue()));
        byte[] callData =
                tx.isContractCreationTransaction()
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : ArrayUtils.nullToEmpty(tx.getData());

        /*
         * execution info
         */
        int depth = 0;
        int kind =
                tx.isContractCreationTransaction()
                        ? ExecutionContext.CREATE
                        : ExecutionContext.CALL;
        int flags = 0;

        /*
         * block info
         */
        AionAddress blockCoinbase = block.getCoinbase();
        long blockNumber = block.getNumber();
        long blockTimestamp = block.getTimestamp();
        long blockNrgLimit = block.getNrgLimit();

        // TODO: temp solution for difficulty length
        byte[] diff = block.getDifficulty();
        if (diff.length > 16) {
            diff = Arrays.copyOfRange(diff, diff.length - 16, diff.length);
        }
        DataWord blockDifficulty = new DataWord(diff);

        /*
         * execution and context and results
         */
        ctx =
                new ExecutionContext(
                        txHash,
                        address,
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);

        exeResult = new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrgLimit, null);
    }

    /** Creates a transaction executor (use block nrg limit). */
    public TransactionExecutor(
            AionTransaction tx,
            IAionBlock block,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            boolean isLocalCall,
            Logger logger) {
        this(tx, block, repo, isLocalCall, block.getNrgLimit(), logger);
    }

    /** Create a transaction executor (non constant call, use block nrg limit). */
    public TransactionExecutor(
            AionTransaction tx,
            IAionBlock block,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            Logger logger) {
        this(tx, block, repo, false, block.getNrgLimit(), logger);
    }

    public void setExecutorProvider(ExecutorProvider provider) {
        this.provider = provider;
    }

    /** Execute the transaction */
    public AionTxExecSummary execute() {
        return (AionTxExecSummary) execute(tx, ctx.getTransactionEnergyLimit());
    }

    /** Prepares contract call. */
    protected void call() {
        KernelInterfaceForFastVM kernel =
                new KernelInterfaceForFastVM(repoTrack, askNonce, isLocalCall);

        IPrecompiledContract pc = this.provider.getPrecompiledContract(this.ctx, kernel);
        if (pc != null) {
            exeResult = pc.execute(tx.getData(), ctx.getTransactionEnergyLimit());
        } else {
            // execute code
            byte[] code = repoTrack.getCode(tx.getDestinationAddress());
            if (!ArrayUtils.isEmpty(code)) {
                VirtualMachine fvm = this.provider.getVM();
                exeResult = fvm.run(code, ctx, kernel);
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, tx.getValue());
        repoTrack.addBalance(tx.getSenderAddress(), txValue.negate());
        repoTrack.addBalance(tx.getDestinationAddress(), txValue);
    }

    /** Prepares contract create. */
    protected void create() {
        AionAddress contractAddress = tx.getContractAddress();

        if (repoTrack.hasAccountState(contractAddress)) {
            exeResult.setResultCode(FastVmResultCode.FAILURE);
            exeResult.setEnergyRemaining(0);
            return;
        }

        // create account
        repoTrack.createAccount(contractAddress);

        // execute contract deployer
        if (!ArrayUtils.isEmpty(tx.getData())) {
            VirtualMachine fvm = this.provider.getVM();
            KernelInterfaceForFastVM kernel =
                    new KernelInterfaceForFastVM(repoTrack, askNonce, isLocalCall);
            exeResult = fvm.run(tx.getData(), ctx, kernel);

            if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
                repoTrack.saveCode(contractAddress, exeResult.getOutput());
            }
        }

        // transfer value
        BigInteger txValue = new BigInteger(1, tx.getValue());
        repoTrack.addBalance(tx.getSenderAddress(), txValue.negate());
        repoTrack.addBalance(contractAddress, txValue);
    }

    /** Finalize state changes and returns summary. */
    protected AionTxExecSummary finish() {

        SideEffects rootHelper = new SideEffects();
        if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
            rootHelper.merge(ctx.getSideEffects());
        } else {
            rootHelper.addInternalTransactions(ctx.getSideEffects().getInternalTransactions());
        }

        AionTxExecSummary.Builder builder =
                AionTxExecSummary.builderFor(getReceipt(rootHelper.getExecutionLogs())) //
                        .logs(rootHelper.getExecutionLogs()) //
                        .deletedAccounts(rootHelper.getAddressesToBeDeleted()) //
                        .internalTransactions(rootHelper.getInternalTransactions()) //
                        .result(exeResult.getOutput());

        ResultCode resultCode = exeResult.getResultCode();

        if (resultCode.isSuccess()) {
            repoTrack.flush();
        } else if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        AionTxExecSummary summary = builder.build();

        updateRepo(summary, tx, block.getCoinbase(), rootHelper.getAddressesToBeDeleted());

        return summary;
    }

    /** Returns the transaction receipt. */
    protected AionTxReceipt getReceipt(List<IExecutionLog> logs) {
        //        AionTxReceipt receipt = new AionTxReceipt();
        //        receipt.setTransaction(tx);
        //        receipt.setLogs(txResult.getLogs());
        //        receipt.setNrgUsed(getNrgUsed(tx.nrgLimit()));
        //        receipt.setTransactionResult(exeResult.getOutput());
        //        receipt
        //            .setError(exeResult.getCode() == ResultCode.SUCCESS ? "" :
        // exeResult.getCode().name());
        //
        //        return receipt;
        return (AionTxReceipt) buildReceipt(new AionTxReceipt(), tx, logs);
    }

    public TransactionContext getContext() {
        return ctx;
    }

    public TransactionResult getResult() {
        return exeResult;
    }
}
