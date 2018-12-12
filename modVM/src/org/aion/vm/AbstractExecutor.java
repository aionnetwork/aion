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
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;
import org.aion.base.type.ITxReceipt;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.slf4j.Logger;

public abstract class AbstractExecutor {
    protected static Logger LOGGER;
    protected static Object lock = new Object();
    protected IRepository repo;
    protected IRepositoryCache repoTrack;
    private boolean isLocalCall;
    protected FastVmTransactionResult exeResult;
    private long blockRemainingNrg;
    private boolean askNonce = true;

    public AbstractExecutor(
            IRepository _repo, boolean _localCall, long _blkRemainingNrg, Logger _logger) {
        this.repo = _repo;
        this.repoTrack = repo.startTracking();
        this.isLocalCall = _localCall;
        this.blockRemainingNrg = _blkRemainingNrg;
        LOGGER = _logger;
    }

    protected ITxExecSummary execute(TransactionInterface tx, long contextNrgLmit) {
        synchronized (lock) {
            // prepare, preliminary check
            if (prepare(tx, contextNrgLmit)) {

                if (!isLocalCall) {
                    IRepositoryCache track = repo.startTracking();
                    // increase nonce
                    if (askNonce) {
                        track.incrementNonce(tx.getSenderAddress());
                    }

                    // charge nrg cost
                    // Note: if the tx is a inpool tx, it will temp charge more balance for the
                    // account
                    // once the block info been updated. the balance in pendingPool will correct.
                    BigInteger nrgLimit = BigInteger.valueOf(tx.getEnergyLimit());
                    BigInteger nrgPrice = BigInteger.valueOf(tx.getEnergyPrice());
                    BigInteger txNrgCost = nrgLimit.multiply(nrgPrice);
                    track.addBalance(tx.getSenderAddress(), txNrgCost.negate());
                    track.flush();
                }

                // run the logic
                if (tx.isContractCreationTransaction()) {
                    create();
                } else {
                    call();
                }
            }

            // finalize
            return finish();
        }
    }

    /**
     * Checks that the transaction passes the basic validation criteria. These criteria are: 1. the
     * transaction energy limit is within the acceptable limit range and is larger than the
     * remaining energy in the block that contains the transaction. 2. contextNrgLimit is
     * non-negative. 3. the transaction nonce is equal to the transaction sender's nonce. 4. the
     * transaction sender has enough funds to cover the cost of the transaction.
     *
     * <p>Returns true if all crtieria are met or if the call is local. Returns false if the call is
     * not local and at least one criterion is not met. In this case, the execution result has its
     * result code and energy left set appropriately.
     *
     * @param tx The transaction to check.
     * @param contextNrgLmit The execution context's energy limit.
     * @return true if call is local or if all criteria listed above are met.
     */
    protected final boolean prepare(TransactionInterface tx, long contextNrgLmit) {
        if (isLocalCall) {
            return true;
        }

        BigInteger txNrgPrice = BigInteger.valueOf(tx.getEnergyPrice());
        long txNrgLimit = tx.getEnergyLimit();

        if (tx.isContractCreationTransaction()) {
            if (!isValidNrgContractCreate(txNrgLimit)) {
                exeResult.setResultCodeAndEnergyRemaining(FastVmResultCode.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        } else {
            if (!isValidNrgTx(txNrgLimit)) {
                exeResult.setResultCodeAndEnergyRemaining(FastVmResultCode.INVALID_NRG_LIMIT, txNrgLimit);
                return false;
            }
        }

        if (txNrgLimit > blockRemainingNrg || contextNrgLmit < 0) {
            exeResult.setResultCodeAndEnergyRemaining(FastVmResultCode.INVALID_NRG_LIMIT, 0);
            return false;
        }

        // check nonce
        if (askNonce) {
            BigInteger txNonce = new BigInteger(1, tx.getNonce());
            BigInteger nonce = repo.getNonce(tx.getSenderAddress());

            if (!txNonce.equals(nonce)) {
                exeResult.setResultCodeAndEnergyRemaining(FastVmResultCode.INVALID_NONCE, 0);
                return false;
            }
        }

        // check balance
        BigInteger txValue = new BigInteger(1, tx.getValue());
        BigInteger txTotal = txNrgPrice.multiply(BigInteger.valueOf(txNrgLimit)).add(txValue);
        BigInteger balance = repo.getBalance(tx.getSenderAddress());
        if (txTotal.compareTo(balance) > 0) {
            exeResult.setResultCodeAndEnergyRemaining(FastVmResultCode.INSUFFICIENT_BALANCE, 0);
            return false;
        }

        // TODO: confirm if signature check is not required here

        return true;
    }

    protected abstract ITxExecSummary finish();

    protected abstract void call();

    protected abstract void create();

    /**
     * Tells the ContractExecutor to bypass incrementing the account's nonce when execute is called.
     */
    public void setBypassNonce() {
        this.askNonce = false;
    }

    /**
     * Returns the energy remaining after the transaction was executed. Prior to execution this
     * method simply returns the energy limit for the transaction.
     *
     * @return The energy left after the transaction executes or its energy limit prior to
     *     execution.
     */
    protected long getNrgLeft() {
        return exeResult.getEnergyRemaining();
    }

    /**
     * Returns the energy remaining after the amount of leftover energy from the transaction
     * execution is deducted from limit.
     *
     * @param limit The upper bound to deduct the transaction energy remainder from.
     * @return the energy used as defined above.
     */
    private long getNrgUsed(long limit) {
        return limit - exeResult.getEnergyRemaining();
    }

    /**
     * Builds a new transaction receipt on top of receipt out of tx and logs.
     *
     * @param receipt The receipt to build off of.
     * @param tx The transaction to which this receipt corresponds.
     * @param logs The logs relating to the transaction execution.
     * @return receipt with the new receipt added to it.
     */
    @SuppressWarnings("unchecked")
    protected ITxReceipt buildReceipt(ITxReceipt receipt, ITransaction tx, List logs) {
        // TODO probably remove receipt and instantiate a new empty one here?
        receipt.setTransaction(tx);
        receipt.setLogs(logs);
        receipt.setNrgUsed(getNrgUsed(tx.getEnergyLimit())); // amount of energy used to execute tx
        receipt.setExecutionResult(exeResult.getOutput()); // misnomer -> output is named result
        receipt.setError(
                exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()
                        ? ""
                        : exeResult.getResultCode().name());

        return receipt;
    }

    /**
     * Updates the repository only if the call is not local and the transaction summary was not
     * marked as rejected.
     *
     * <p>If the repository qualifies for an update then it is updated as follows: 1. The
     * transaction sender is refunded for whatever outstanding energy was not consumed. 2. The
     * transaction energy consumption amount is set accordingly. 3. The fee is transferred to the
     * coinbase account. 4. All accounts marked for deletion (given that the transaction was
     * successful) are deleted.
     *
     * @param summary The transaction summary.
     * @param tx The transaction.
     * @param coinbase The coinbase for the block in which the transaction was sealed.
     * @param deleteAccounts The list of accounts to be deleted if tx was successful.
     */
    protected void updateRepo(
            ITxExecSummary summary,
            ITransaction tx,
            Address coinbase,
            List<Address> deleteAccounts) {

        if (!isLocalCall && !summary.isRejected()) {
            IRepositoryCache track = repo.startTracking();
            // refund nrg left
            if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()
                    || exeResult.getResultCode().toInt() == FastVmResultCode.REVERT.toInt()) {
                track.addBalance(tx.getSenderAddress(), summary.getRefund());
            }

            tx.setNrgConsume(getNrgUsed(tx.getEnergyLimit()));

            // Transfer fees to miner
            track.addBalance(coinbase, summary.getFee());

            if (exeResult.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
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

    protected void setTransactionResult(FastVmTransactionResult result) {
        exeResult = result;
    }

}
