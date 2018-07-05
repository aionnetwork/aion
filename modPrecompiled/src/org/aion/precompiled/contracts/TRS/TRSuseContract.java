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
     *       amount is the amount of funds to deposit. The contract interprets these 128 bytes as an
     *       unsigned and positive amount.
     *
     *     conditions: the calling account must have enough balance to deposit amount otherwise this
     *       method effectively does nothing. The deposit operation is enabled only when the contract
     *       is unlocked; once locked depositing is disabled.
     *
     *     returns: void.
     *
     *                                          ~~~***~~~
     *
     *   <b>operation 0x5</b> - refunds a specified amount of depositor's balance for the public-
     *     facing TRS contract.
     *     [<32b - contractAddress> | <32b - accountAddress> | <128b - amount>]
     *     total = 193 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract that the account given
     *       by the address accountAddress is being refunded from. The proposed refund amount is
     *       given by amount. The contract interprets the 128 bytes of amount as unsigned and
     *       strictly positive.
     *
     *     conditions: the calling account must be the owner of the public-facing TRS contract. The
     *       account accountAddress must have a deposit balannce into this TRS contract of at least
     *       amount. The refund operation is enabled only when the contract is unlocked; once locked
     *       refunding is disabled. If any of these conditions are not satisfied this method
     *       effectively does nothing.
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
            case 5: return refund(input, nrgLimit);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Logic to deposit funds to an existing public-facing TRS contract.
     *
     * The input byte array format is defined as follows:
     *   [<1b - 0x0> | <32b - contractAddress> | <128b - amount>]
     *   total = 161 bytes
     * where:
     *   contractAddress is the address of the public-facing TRS contract to deposit funds into.
     *   amount is the amount of funds to deposit. The contract interprets these 128 bytes as an
     *     unsigned and positive amount.
     *
     *   conditions: the calling account must have enough balance to deposit amount otherwise this
     *     method effectively does nothing. The deposit operation is enabled only when the contract
     *     is unlocked; once locked depositing is disabled. Note that if zero is deposited, the call
     *     will succeed but the depositor will not be saved into the database.
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

        //TODO: ensure caller has AION PREFIX -- good reminder if this ever changes.

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address contract = Address.wrap(Arrays.copyOfRange(input, indexAddress, indexAmount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A deposit operation can only execute if direct depositing is enabled or caller is owner.
        Address owner = getContractOwner(contract);
        if (!caller.equals(owner) && !isDirDepositsEnabled(specs)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A deposit operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity).
        if (isContractLocked(specs) || isContractLive(specs)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The caller must have adequate funds to make the proposed deposit.
        BigInteger fundsAvailable = track.getBalance(caller);
        if (fundsAvailable.compareTo(amount) < 0) {
            return new ContractExecutionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        // If deposit amount is larger than zero, update the depositor's current deposit balance and
        // then update the deposit meta-data (linked list, count, etc.)
        if (amount.compareTo(BigInteger.ZERO) > 0) {
            BigInteger currAmount = getDepositBalance(contract, caller);
            if (!setDepositBalance(contract, caller, currAmount.add(amount))) {
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
            }
            listAddCallerToHead(contract);
            setTotalBalance(contract, getTotalBalance(contract).add(amount));
            track.addBalance(caller, amount.negate());
            track.flush();
        }
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Logic to refund funds for an account in an existing public-facing TRS contract.
     *
     * The input byte array format is defined as follows:
     *     [<1b - 0x5> | <32b - contractAddress> | <32b - accountAddress> | <128b - amount>]
     *     total = 193 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract that the account given
     *       by the address accountAddress is being refunded from. The proposed refund amount is
     *       given by amount. The contract interprets the 128 bytes of amount as unsigned and
     *       strictly positive.
     *
     *     conditions: the calling account must be the owner of the public-facing TRS contract. The
     *       account accountAddress must have a deposit balannce into this TRS contract of at least
     *       amount. The refund operation is enabled only when the contract is unlocked; once locked
     *       refunding is disabled. If any of these conditions are not satisfied this method
     *       effectively does nothing.
     *
     *     returns: void.
     *
     * @param input The input to refund an account for a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult refund(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int indexAccount = 33;
        final int indexAmount = 65;
        final int len = 193;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address contract = Address.wrap(Arrays.copyOfRange(input, indexContract, indexAccount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A refund operation can only execute if the caller is the contract owner.
        Address owner = getContractOwner(contract);
        if (!caller.equals(owner)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A refund operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity).
        if (isContractLocked(specs) || isContractLive(specs)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Ensure the account exists (ie. has a positive deposit balance for the contract).
        Address account = Address.wrap(Arrays.copyOfRange(input, indexAccount, indexAmount));
        BigInteger accountBalance = getDepositBalance(contract, account);
        if (accountBalance.equals(BigInteger.ZERO)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The account must have a deposit balance large enough to make the refund.
        BigInteger newBalance = accountBalance.subtract(amount);
        if (newBalance.compareTo(BigInteger.ZERO) < 0) {
            return new ContractExecutionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        // If refund amount is larger than zero, update the depositor's current deposit balance and
        // then update the deposit meta-data (linked list, count, etc.) and refund the account.
        if (amount.compareTo(BigInteger.ZERO) > 0) {
            if (!setDepositBalance(contract, account, newBalance)) {
                return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
            }
            setTotalBalance(contract, getTotalBalance(contract).subtract(amount));

            // If the account's balance drops to zero, delete account from the TRS contract.
            if (newBalance.equals(BigInteger.ZERO)) {
                listRemoveAccount(contract, account);
            }

            track.addBalance(account, amount);
            track.flush();
        }
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }


    // <-------------------------------------HELPER METHODS---------------------------------------->

    /**
     * Updates the linked list for the TRS contract given by contract, if necessary, in the following
     * way:
     *
     * If the caller does not have a valid account in the TRS contract contract then that account is
     * added to the head of the linked list for that contract.
     *
     * If the account is valid and already exists this method does nothing, since the caller must
     * already be in the list.
     *
     * @param contract The TRS contract to update.
     */
    private void listAddCallerToHead(Address contract) {
        byte[] next = getListNextBytes(contract, caller);
        if (accountIsValid(next)) { return; }  // no update needed.

        // Set caller's next entry to point to head.
        byte[] head = getListHead(contract);
        setListNext(contract, caller, next[0], head, true);

        // If head was non-null set the head's previous entry to point to caller.
        if (head != null) {
            head[0] = AION_PREFIX;
            setListPrevious(contract, Address.wrap(head), Arrays.copyOf(caller.toBytes(), Address.ADDRESS_LEN));
        }

        // Set the head of the list to point to caller and set caller's previous entry to null.
        setListHead(contract, Arrays.copyOf(caller.toBytes(), Address.ADDRESS_LEN));
        setListPrevious(contract, caller, null);
    }

    /**
     * Updates the linked list for the TRS contract given by contract, if necessary, in the following
     * way:
     *
     * If the account is valid and exists in contract then that account is removed from the linked
     * list for contract and the account is marked as invalid since we cannot have valid accounts
     * that are not in the list.
     *
     * If the account is not valid for the TRS contract this method does nothing, since the account
     * must not be in the list anyway.
     *
     * @param contract The TRS contract to update.
     * @param account The account to remove from the list.
     */
    private void listRemoveAccount(Address contract, Address account) {
        byte[] prev = getListPrev(contract, account);
        byte[] next = getListNext(contract, account);

        // If account is head of list, set head to account's next entry.
        byte[] head = getListHead(contract);    //TODO: maybe decouple metadata from next so we dont
        head[0] = AION_PREFIX;                  //TODO: have to do awkward things like this?
        if (Arrays.equals(account.toBytes(), head)) {
            setListHead(contract, getListNext(contract, account));
        }

        // If account has a previous entry, set that entry's next entry to account's next entry.
        if (prev != null) {
            byte[] prevCopy = Arrays.copyOf(prev, prev.length);
            prevCopy[0] = AION_PREFIX;
            byte[] prevNext = getListNext(contract, prevCopy);
            setListNext(contract, prevCopy, prevNext[0], next, true);
        }

        // If account has a next entry, set that entry's previous entry to account's previous entry.
        if (next != null) {
            setListPrevious(contract, next, prev);
        }

        // Mark account as invalid (trumps setting next to null), set its previous to null.
        setListNext(contract, account, (byte) 0x0, null, false);
        setListPrevious(contract, account, null);
    }

}