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

import java.util.ArrayList;
import org.aion.base.db.IRepository;
import org.aion.mcf.vm.AbstractExecutionResult.ResultCode;
import org.aion.mcf.vm.AbstractExecutor;
import org.aion.mcf.vm.IPrecompiledContract;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * The executor of pre-compiled contracts.
 */
public class ContractExecutor extends AbstractExecutor {

    private long nrgLimit;
    private IAionBlock block;
    private AionTransaction tx;

    /**
     * Constructs a new ContractExecutor.
     *
     * @param tx The transaction to execute.
     * @param block The block.
     * @param repo The database.
     * @param isLocalCall Whether is a local call or not.
     * @param blockRemainingNrg The remaining block energy left.
     * @param logger The LOGGER.
     */
    public ContractExecutor(AionTransaction tx, IAionBlock block, IRepository repo,
        boolean isLocalCall, long blockRemainingNrg, Logger logger) {

        super(repo, isLocalCall, blockRemainingNrg, logger);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing transaction: {}", tx);
        }

        this.tx = tx;
        this.repo = repo;
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
     * @param logger The LOGGER.
     */
    public ContractExecutor(AionTransaction tx, IAionBlock block, IRepository repo, Logger logger) {
        this(tx, block, repo, false, block.getNrgLimit(), logger);
    }

    /**
     * Execute the transaction.
     */
    public AionTxExecSummary execute() {
        return (AionTxExecSummary) execute(tx, nrgLimit);
    }

    /**
     * Performs the contract call.
     */
    protected void call() {
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
            exeResult.setCodeAndNrgLeft(ResultCode.INTERNAL_ERROR.toInt(), 0);
        }
    }

    @Override
    protected void create() {
        throw new UnsupportedOperationException(
            "Can't create solidity contract inside the precompiled-contract");
    }

    /**
     * Finalize state changes and returns a summary.
     *
     * @return the execution summary.
     */
    protected AionTxExecSummary finish() {

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(getReceipt()) //
            .logs(new ArrayList<>()) //
            .deletedAccounts(new ArrayList<>()) //
            .internalTransactions(new ArrayList<>()) //
            .result(exeResult.getOutput());

        switch (ResultCode.fromInt(exeResult.getCode())) {
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

        updateRepo(summary, tx, block.getCoinbase(), new ArrayList<>());

        return summary;
    }

    /**
     * Returns the transaction receipt.
     *
     * @return the transaction receipt.
     */
    protected AionTxReceipt getReceipt() {
        return (AionTxReceipt) buildReceipt(new AionTxReceipt(), tx, new ArrayList<>());
    }
}
