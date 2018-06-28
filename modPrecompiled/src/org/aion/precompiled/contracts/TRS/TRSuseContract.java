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
package org.aion.precompiled.contracts.TRS;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;

/**
 * The TRSuseContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since the
 * TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * The TRSuseContract is the component of the public-facing TRS contract that users of the contract
 * (as well as the owner) interact with in order to perform some state-changing operation on the
 * contract itself. Some of the supported operations are privileged and can only be called by the
 * contract owner.
 *
 * The following operations are supported:
 *      deposit -- deposits funds into a public-facing TRS contract.
 *      withdraw -- withdraws funds from a public-facing TRS contract.
 *      bulkDeposit -- bulk fund depositing on the behalf of depositors. Owner-only.
 *      bulkWithdraw -- bulk fund withdrawal to all depositors. Owner-only.
 *      depositBonus -- deposits bonus funds into a public-facing TRS contract.
 *      refund -- refunds funds to a depositor. Can only be called prior to locking. Owner-only.
 *      updateTotal -- updates the total balance, in case of subsequent sales. Owner-only.
 */
public final class TRSuseContract extends AbstractTRS {
    private static final long COST = 21000L;    // temporary.

    //TODO: keys need to be static in AbstractTRS

    /**
     * Constructs a new TRSuseContract that will use repo as the database cache to update its
     * state with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public TRSuseContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo, Address caller) {

        super(repo, caller);
    }

    /**
     * The input byte array provided to this method must have the following format:
     *
     * [<1b - operation> | <arguments>]
     *
     * where arguments is defined differently for different operations. The supported operations
     * along with their expected arguments are outlined as follows:
     *
     *   <b>operation 0x0</b> - deposits funds into a public-facing TRS contract.
     *     [<32b - contractAddress> | <128b - amount>]
     *     total = 161 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract to deposit funds into.
     *     amount is the amount of funds to deposit. The contract interprets these 128 bytes as an
     *       unsigned and positive amount.
     *
     *     conditions: the calling account must have enough balance to deposit amount otherwise this
     *       method effectively does nothing. The deposit operation is enabled only when the contract
     *       is unlocked; once locked depositing is disabled.
     *
     *     returns: void.
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of calling execute on the specified input.
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrgLimit) {
        if (input == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if (input.length == 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        if (nrgLimit < COST) {
            return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0);
        }
        if (!isValidTxNrg(nrgLimit)) {
            return new ContractExecutionResult(ResultCode.INVALID_NRG_LIMIT, 0);
        }

        int operation = input[0];
        switch (operation) {
            case 0: return deposit(input, nrgLimit);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Logic to deposit funds to an existing public-facing TRS contract.
     *
     * The input byte array format is defined as follows:
     *   [<32b - contractAddress> | <128b - amount>]
     *   total = 161 bytes
     * where:
     *   contractAddress is the address of the public-facing TRS contract to deposit funds into.
     *   amount is the amount of funds to deposit. The contract interprets these 128 bytes as an
     *     unsigned and positive amount.
     *
     *   conditions: the calling account must have enough balance to deposit amount otherwise this
     *     method effectively does nothing. The deposit operation is enabled only when the contract
     *     is unlocked; once locked depositing is disabled.
     *
     *   returns: void.
     *
     * @param input The input to deposit to a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult deposit(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int indexAmount = 33;
        final int len = 161;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Grab the contract address and put amount in a byte array one byte larger with an empty
        // initial byte so that amount will be interpreted as unsigned.
        Address contract = Address.wrap(Arrays.copyOfRange(input, indexAddress, indexAmount));
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // A deposit operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity).
        IDataWord specs = fetchContractSpecs(contract);
        if (specs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] specBytes = specs.getData();
        if (isContractLocked(specBytes) || isContractLive(specBytes)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // The caller must have adequate funds to make the proposed deposit.
        BigInteger fundsAvailable = track.getBalance(caller);
        if (fundsAvailable.compareTo(amount) < 0) {
            return new ContractExecutionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        // First, update this depositor's current deposit balance.
        int numRows = updateBalance(contract, amountBytes, amount);

        // Second, update the linked list or any meta-data.
        updateDeposit(contract, numRows);

        track.flush();
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Updates this depositor's current deposit balance. If the depositor has no balance then the
     * deposited amount will be added. If a balance already exists for this depositor then the new
     * balance will be equal to the old one plus the amount to be deposited.
     *
     * Returns the number of rows used to store the depositor's balance in the repository.
     *
     * @param contract The TRS contract.
     * @param amount The amount to deposit for the caller.
     */
    private int updateBalance(Address contract, byte[] amount, BigInteger amountBI) {
        int len = amount.length - 1;
        int numRows = (int) Math.ceil(len / (double) DoubleDataWord.BYTES);

        IDataWord value = track.getStorageValue(contract, new DoubleDataWord(caller.toBytes()));
        if (value == null) {
            // Then no entry for this depositor currently exists. We create one.
            byte[] newBal = new byte[numRows * DoubleDataWord.BYTES];
            System.arraycopy(amount, 1, newBal, newBal.length - len, len);
            updateBalanceOverRange(contract, 0, numRows - 1, newBal);
        } else {
            // Entry exists, we update it.
            numRows = (len % DoubleDataWord.BYTES == 0) ? numRows + 1 : numRows;
            BigInteger currBal = fetchBalance(contract, 0, numRows - 1);
            BigInteger newBal = currBal.add(amountBI);
            updateBalanceOverRange(contract, 0, numRows - 1, newBal.toByteArray());
        }
        return numRows;
    }

    /**
     * Updates the meta-data associated with the current caller's balance. When this call returns
     * the caller's meta-data will reflect the fact that numRows storage rows are in use to store
     * the current deposit balance and the linked list joining all depositors will be updated, if
     * necessary, to include the caller.
     *
     * This method does not flush!
     *
     * @param contract The contract to update.
     * @param numRows The number of rows in use to hold the caller's deposit balance.
     */
    private void updateDeposit(Address contract, int numRows) {
        // 1. Check if valid bit is set and if so unset it.
        // 2. If valid bit was unset no need to update 'next', check if numRows differs, if so update.
        // 3. If valid bit was set, set 'next' to 'head', set 'prev' to null, place self in 'head',
        //    check if numRows differs, if so update.
        byte[] meta = track.getStorageValue(contract, new DoubleDataWord(caller.toBytes())).getData();
        if ((meta[0] & 0x80) == 0x80) {
            // Valid bit was set so the caller already exists and has a deposit balance.
            int oldRows = meta[1];
            if (numRows != oldRows) {
                meta[1] = 0x0;
                meta[1] = (byte) numRows;
                track.addStorageRow(contract, new DoubleDataWord(caller.toBytes()), new DoubleDataWord(meta));
            }
        } else {
            // Valid bit unset, add caller as head of linked list and update meta-data.
            byte[] key = new byte[DoubleDataWord.BYTES];
            key[0] = DEPOSITS_CODE;
            byte[] next = track.getStorageValue(contract, new DoubleDataWord(key)).getData();
//            next[0] = AION_PREFIX;

            // Unset the null bit.
        }
    }

    /**
     * Returns the caller's deposit balance by considering only rows in the range [start, stop] in
     * the storage -- the assumption is that these are valid row indices -- as a BigInteger.
     *
     * @param contract The contract to query.
     * @param start The row starting index.
     * @param stop The row stopping index.
     * @return a BigInteger of the balance over the specified range.
     */
    private BigInteger fetchBalance(Address contract, int start, int stop) {
        byte[] bal = new byte[(stop - start) * DoubleDataWord.BYTES];
        for (int i = stop; i >= start; i--) {
            byte[] key = new byte[DoubleDataWord.BYTES];
            key[0] = (byte) (DEPOSITS_CODE | i);
            byte[] val = track.getStorageValue(contract, new DoubleDataWord(key)).getData();
            System.arraycopy(val, 0, bal, (i - start) * DoubleDataWord.BYTES, DoubleDataWord.BYTES);
        }
        return new BigInteger(bal);
    }

    /**
     * Saves, overwriting if necessary, the new balance represented by newBal in the storage over
     * rows in the range [start, stop]. The assumption is that this range can hold the array.
     *
     * This method does not flush!
     *
     * @param contract The contract to update.
     * @param start The row starting index.
     * @param stop The row stopping index.
     * @param newBal The byte array of the balance to update over the specified range.
     */
    private void updateBalanceOverRange(Address contract, int start, int stop, byte[] newBal) {
        for (int i = start; i <= stop; i++) {
            byte[] key = new byte[DoubleDataWord.BYTES];
            key[0] = (byte) (DEPOSITS_CODE | i);
            byte[] val = Arrays.copyOfRange(newBal, (i - start) * DoubleDataWord.BYTES,
                (i + 1 - start) * DoubleDataWord.BYTES);
            track.addStorageRow(contract, new DoubleDataWord(key), new DoubleDataWord(val));
        }
    }

}