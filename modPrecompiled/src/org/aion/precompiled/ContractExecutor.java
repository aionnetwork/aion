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
package org.aion.precompiled;

import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.IPrecompiledContract;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * The executor of pre-compiled contracts.
 */
public class ContractExecutor {
    private static final Object lock = new Object();
    private final Logger contractLogger;
    private boolean isLocalCall;
    private long blockRemainingNrg;
    private boolean askNonce = true;
    private long nrgLimit;
    private IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    private IAionBlock block;
    private AionTransaction tx;
    private IRepositoryCache repoTrack;
    private ContractExecutionResult exeResult;

    /**
     * Constructs a new ContractExecutor.
     *
     * @param tx The transaction to execute.
     * @param block The block.
     * @param repo The database.
     * @param isLocalCall Whether is a local call or not.
     * @param blockRemainingNrg The remaining block energy left.
     * @param logger The logger.
     */
    public ContractExecutor(AionTransaction tx, IAionBlock block,
        IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo, boolean isLocalCall,
        long blockRemainingNrg, Logger logger) {

        contractLogger = logger;
        if (logger.isDebugEnabled()) {
            logger.debug("Executing transaction: {}", tx);
        }

        this.tx = tx;
        this.repo = repo;
        this.repoTrack = this.repo.startTracking();
        this.isLocalCall = isLocalCall;
        this.blockRemainingNrg = blockRemainingNrg;
        this.block = block;
        this.nrgLimit = tx.nrgLimit() - tx.transactionCost(block.getNumber());
        this.exeResult = new ContractExecutionResult(ResultCode.SUCCESS, this.nrgLimit);
    }

    /**
     * Constructs a new ContractExecutor that is not a local call and whose remaining block energy
     * is the energy limit of the block parameter.
     *
     * @param tx The transaction to execute.
     * @param block The block.
     * @param repo The database.
     * @param logger The logger.
     */
    public ContractExecutor(AionTransaction tx, IAionBlock block,
        IRepository<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo, Logger logger) {

        this(tx, block, repo, false, block.getNrgLimit(), logger);
    }

    /**
     * Execute the transaction.
     */
    public AionTxExecSummary execute() {
        synchronized (lock) {
            // prepare, preliminary check
            if (prepare()) {

                if (!isLocalCall) {
                    IRepositoryCache track = repo.startTracking();
                    // increase nonce
                    if (askNonce) {
                        track.incrementNonce(tx.getFrom());
                    }

                    // charge nrg cost
                    // Note: if the tx is a inpool tx, it will temp charge more balance for the account
                    // once the block info been updated. the balance in pendingPool will correct.
                    BigInteger txNrgLimit = BigInteger.valueOf(tx.nrgLimit());
                    BigInteger txNrgPrice = tx.nrgPrice().value();
                    BigInteger txNrgCost = txNrgLimit.multiply(txNrgPrice);
                    track.addBalance(tx.getFrom(), txNrgCost.negate());
                    track.flush();
                }

                // run the logic
                call();
            }

            // finalize
            return finish();
        }
    }

    /**
     * Prepares for transaction execution.
     */
    private boolean prepare() {
        if (isLocalCall) {
            return true;
        }

        // check nrg limit
        BigInteger txNrgPrice = tx.nrgPrice().value();
        long txNrgLimit = tx.nrgLimit();

        // may need separate energy rules for pre-comp contracts?
        if (tx.isContractCreation()) {
            if (!TxNrgRule.isValidNrgContractCreate(txNrgLimit)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        } else {
            if (!TxNrgRule.isValidNrgTx(txNrgLimit)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        }

        if (txNrgLimit > blockRemainingNrg || this.nrgLimit < 0) {
            exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT, 0);
            return false;
        }

        // check nonce
        if (askNonce) {
            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger nonce = repo.getNonce(tx.getFrom());

            if (!txNonce.equals(nonce)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NONCE, 0);
                return false;
            }
        }

        // check balance
        BigInteger txValue = new BigInteger(1, tx.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        BigInteger balance = repo.getBalance(tx.getFrom());
        if (txTotal.compareTo(balance) > 0) {
            exeResult.setCodeAndNrgLeft(ResultCode.INSUFFICIENT_BALANCE, 0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    /**
     * Performs the contract call.
     */
    private void call() {
        IPrecompiledContract pc = ContractFactory.getPrecompiledContract(tx.getTo(), tx.getFrom(),
            this.repoTrack);

        if (pc != null) {
            exeResult = pc.execute(tx.getData(), this.nrgLimit);

            // transfer value
            /*
            BigInteger txValue = new BigInteger(1, tx.getValue());
            repoTrack.addBalance(tx.getFrom(), txValue.negate());
            repoTrack.addBalance(tx.getTo(), txValue);
            */
        } else {
            exeResult.setCodeAndNrgLeft(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Finalize state changes and returns a summary.
     *
     * @return the execution summary.
     */
    private AionTxExecSummary finish() {

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(getReceipt()) //
            .logs(new ArrayList<>()) //
            .deletedAccounts(new ArrayList<>()) //
            .internalTransactions(new ArrayList<>()) //
            .result(exeResult.getOutput());

        switch (exeResult.getCode()) {
            case SUCCESS:
                repoTrack.flush();
                break;
            case INVALID_NONCE:
            case INVALID_NRG_LIMIT:
            case INSUFFICIENT_BALANCE:
                builder.markAsRejected();
                break;
            case FAILURE:
            case OUT_OF_NRG:
            case REVERT:
            case INTERNAL_ERROR:
                builder.markAsFailed();
                break;
            default:
                throw new RuntimeException("invalid code path, should not ever default");
        }

        AionTxExecSummary summary = builder.build();

        if (!isLocalCall && !summary.isRejected()) {
            IRepositoryCache track = repo.startTracking();
            // refund nrg left
            if (exeResult.getCode() == ResultCode.SUCCESS || exeResult.getCode() == ResultCode.REVERT) {
                track.addBalance(tx.getFrom(), summary.getRefund());
            }

            tx.setNrgConsume(tx.nrgLimit() - exeResult.getNrgLeft());

            // Transfer fees to miner
            track.addBalance(block.getCoinbase(), summary.getFee());

            track.flush();
        }

        if (contractLogger.isDebugEnabled()) {
            contractLogger.debug("Transaction receipt: {}", summary.getReceipt());
            contractLogger.debug("Transaction logs: {}", summary.getLogs());
        }

        return summary;
    }

    /**
     * Returns the transaction receipt.
     *
     * @return the transaction receipt.
     */
    protected AionTxReceipt getReceipt() {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(tx);
        receipt.setLogs(new ArrayList<>());
        receipt.setNrgUsed(tx.nrgLimit() - exeResult.getNrgLeft());
        receipt.setExecutionResult(exeResult.getOutput());
        receipt.setError(exeResult.getCode() == ResultCode.SUCCESS ? "" : exeResult.getCode().name());
        return receipt;
    }

    /**
     * Tells the ContractExecutor to bypass incrementing the account's nonce when execute is called.
     */
    public void setBypassNonce() {
        this.askNonce = false;
    }

    /**
     * Tells the ContractExecutor not to bypass incrementing the account's nonce when execute is
     * called.
     */
    public void setNoBypassNonce() {
        this.askNonce = true;
    }

}
