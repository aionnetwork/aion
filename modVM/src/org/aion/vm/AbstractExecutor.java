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

import static org.aion.mcf.valid.TxNrgRule.isValidNrgContractCreate;
import static org.aion.mcf.valid.TxNrgRule.isValidNrgTx;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;
import org.aion.base.type.ITxReceipt;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.slf4j.Logger;

public abstract class AbstractExecutor {
    protected static Logger LOGGER;
    protected static Object lock = new Object();
    protected IRepository repo;
    protected IRepositoryCache repoTrack;
    private boolean isLocalCall;
    protected IExecutionResult exeResult;
    private long blockRemainingNrg;
    private boolean askNonce = true;

    public AbstractExecutor(IRepository _repo, boolean _localCall, long _blkRemainingNrg, Logger _logger) {
        this.repo = _repo;
        this.repoTrack = repo.startTracking();
        this.isLocalCall = _localCall;
        this.blockRemainingNrg = _blkRemainingNrg;
        LOGGER = _logger;
    }

    protected ITxExecSummary execute(ITransaction tx, long contextNrgLmit) {
        synchronized (lock) {
            // prepare, preliminary check
            if (prepare(tx, contextNrgLmit)) {

                if (!isLocalCall) {
                    IRepositoryCache track = repo.startTracking();
                    // increase nonce
                    if (askNonce) {
                        track.incrementNonce(tx.getFrom());
                    }

                    // charge nrg cost
                    // Note: if the tx is a inpool tx, it will temp charge more balance for the account
                    // once the block info been updated. the balance in pendingPool will correct.
                    BigInteger nrgLimit = BigInteger.valueOf(tx.getNrg());
                    BigInteger nrgPrice = BigInteger.valueOf(tx.getNrgPrice());
                    BigInteger txNrgCost = nrgLimit.multiply(nrgPrice);
                    track.addBalance(tx.getFrom(), txNrgCost.negate());
                    track.flush();
                }

                // run the logic
                if (tx.isContractCreation()) {
                    create();
                } else {
                    call();
                }
            }

            // finalize
            return finish();
        }
    }

    private boolean prepare(ITransaction tx, long contextNrgLmit) {
        if (isLocalCall) {
            return true;
        }

        // check nrg limit
        BigInteger txNrgPrice = BigInteger.valueOf(tx.getNrgPrice());
        long txNrgLimit = tx.getNrg();

        if (tx.isContractCreation()) {
            if (!isValidNrgContractCreate(txNrgLimit)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT.toInt(), txNrgLimit);
                return false;
            }
        } else {
            if (!isValidNrgTx(txNrgLimit)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT.toInt(), txNrgLimit);
                return false;
            }
        }

        if (txNrgLimit > blockRemainingNrg || contextNrgLmit < 0) {
            exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NRG_LIMIT.toInt(), 0);
            return false;
        }

        // check nonce
        if (askNonce) {
            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger nonce = repo.getNonce(tx.getFrom());

            if (!txNonce.equals(nonce)) {
                exeResult.setCodeAndNrgLeft(ResultCode.INVALID_NONCE.toInt(), 0);
                return false;
            }
        }

        // check balance
        BigInteger txValue = new BigInteger(1, tx.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        BigInteger balance = repo.getBalance(tx.getFrom());
        if (txTotal.compareTo(balance) > 0) {
            exeResult.setCodeAndNrgLeft(ResultCode.INSUFFICIENT_BALANCE.toInt(), 0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    protected abstract ITxExecSummary finish();

    protected abstract void call();

    protected abstract void create();

    /**
     * Tells the ContractExecutor to bypass incrementing the account's nonce when execute is
     * called.
     */
    public void setBypassNonce() {
        this.askNonce = false;
    }

    /**
     * Returns the nrg left after execution.
     */
    protected long getNrgLeft() {
        return exeResult.getNrgLeft();
    }

    /**
     * Returns the nrg used after execution.
     */
    private long getNrgUsed(long limit) {
        return limit - exeResult.getNrgLeft();
    }

    /**
     * Returns the transaction receipt.
     */
    @SuppressWarnings("unchecked")
    protected ITxReceipt buildReceipt(ITxReceipt receipt, ITransaction tx, List logs) {
        //TODO probably remove receipt and instantiate a new empty one here?
        receipt.setTransaction(tx);
        receipt.setLogs(logs);
        receipt.setNrgUsed(getNrgUsed(tx.getNrg()));    // amount of energy used to execute tx
        receipt.setExecutionResult(exeResult.getOutput());  // misnomer -> output is named result
        receipt.setError(exeResult.getCode() == ResultCode.SUCCESS.toInt() ? ""
            : ResultCode.fromInt(exeResult.getCode()).name());

        return receipt;
    }

    protected void updateRepo(ITxExecSummary summary, ITransaction tx, Address coinbase,
        List<Address> deleteAccounts) {

        if (!isLocalCall && !summary.isRejected()) {
            IRepositoryCache track = repo.startTracking();
            // refund nrg left
            if (exeResult.getCode() == ResultCode.SUCCESS.toInt()
                || exeResult.getCode() == ResultCode.REVERT.toInt()) {
                track.addBalance(tx.getFrom(), summary.getRefund());
            }

            tx.setNrgConsume(getNrgUsed(tx.getNrg()));

            // Transfer fees to miner
            track.addBalance(coinbase, summary.getFee());

            if (exeResult.getCode() == ResultCode.SUCCESS.toInt()) {
                // Delete accounts
                for (Address addr : deleteAccounts) {
                    track.deleteAccount(addr);
                }
            }
            track.flush();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Transaction receipt: {}", summary.getReceipt());
            LOGGER.debug("Transaction logs: {}", summary.getLogs());
        }
    }
}
