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
import org.aion.base.type.AionAddress;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;

/**
 * The TRSuseContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * <p>The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since
 * the TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * <p>The TRSuseContract is the component of the public-facing TRS contract that users of the
 * contract (as well as the owner) interact with in order to perform some state-changing operation
 * on the contract itself. Some of the supported operations are privileged and can only be called by
 * the contract owner.
 *
 * <p>The following operations are supported: deposit -- deposits funds into a public-facing TRS
 * contract. withdraw -- withdraws funds from a public-facing TRS contract. bulkDepositFor -- bulk
 * fund depositing on the behalf of depositors. Owner-only. bulkWithdraw -- bulk fund withdrawal to
 * all depositors. Owner-only. refund -- refunds funds to a depositor. Can only be called prior to
 * locking. Owner-only. depositFor -- deposits funds into a public-facing TRS contract on an
 * accout's behalf. Owner-only. addExtraFunds -- adds extra funds to contract, in case of subsequent
 * sales. Owner-only.
 */
public final class TRSuseContract extends AbstractTRS {

    /**
     * Constructs a new TRSuseContract that will use repo as the database cache to update its state
     * with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public TRSuseContract(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo,
            AionAddress caller,
            IBlockchain blockchain) {

        super(repo, caller, blockchain);
    }

    /**
     * The input byte array provided to this method must have the following format:
     *
     * <p>[<1b - operation> | <arguments>]
     *
     * <p>where arguments is defined differently for different operations. The supported operations
     * along with their expected arguments are outlined as follows:
     *
     * <p><b>operation 0x0</b> - deposits funds into a public-facing TRS contract. [<32b -
     * contractAddress> | <128b - amount>] total = 161 bytes where: contractAddress is the address
     * of the public-facing TRS contract to deposit funds into. amount is the amount of funds to
     * deposit. The contract interprets these 128 bytes as an unsigned and positive amount.
     *
     * <p>conditions: the calling account must have enough balance to deposit amount otherwise this
     * method effectively does nothing. The deposit operation is enabled only when the contract is
     * unlocked; once locked depositing is disabled. If a contract has its funds open then this
     * operation is disabled.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x1</b> - withdraws funds from the calling account's TRS deposit balance and
     * puts them into the calling account. Each account is eligible to make 1 withdrawal per period
     * as well as a special one-off withdrawal that will be claimed automatically on the account's
     * first ever withdrawal. If an account has missed previous periods then the next call to this
     * operation will withdraw for the current period as well as any previously missed periods.
     *
     * <p>In the special case that a contract has its funds open, the first call to this operation
     * after the funds are opened will withdraw all funds that the contract owes the caller
     * including bonus. Any subsequent calls will fail.
     *
     * <p>[<32b - contractAddress>] total = 33 bytes where: contractAddress is the address of the
     * public-facing TRS contract that the account is attempting to withdraw funds from.
     *
     * <p>conditions: the TRS contract must be live in order to withdraw funds from it.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x2</b> - deposits the specified funds into the contract on the behalf of
     * other accounts. This is a convenience operation for the owner to use the depositFor logic in
     * bulk. This operation supports depositing into the contract on behalf of up to 100 accounts.
     *
     * <p>Define "entry" as the following 160b array: [<32b - accountAddress> | <128b - amount>]
     * Then the arguments to this operation are: 32-byte contractAddress followed by a contiguous
     * list of X 160-byte entry arrays, where X is in [1, 100] total = 193-16,033 bytes where:
     * contractAddress is the address of the public-facing TRS contract that the account is
     * attempting to deposit funds into. and for each entry we have the following pair:
     * accountAddress is the address of an account the caller is attempting to deposit funds into
     * the contract for. amount is the corresponding amount of funds to deposit on behalf of
     * accountAddress.
     *
     * <p>conditions: the TRS contract must not yet be locked (nor obviously live) to bulkDeposit
     * into it. The caller must be the owner of the contract. Each accountAddress must be an Aion
     * account address. If a contract has its funds open then this operation is disabled.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x3</b> - withdraws funds from the contract on behalf of each depositor in
     * the contract. This operation is functionally equivalent to having each depositor in the
     * contract perform a withdraw operation.
     *
     * <p>In the special case where a contract has its funds open, the first call to this operation
     * after the funds are opened will withdraw the total amounts the contract owes each depositor.
     * Any subsequent calls to this operation involving the same addresses as the first time will
     * have no effect.
     *
     * <p>[<32b - contractAddress>] total = 33 bytes where: contractAddress is the address of the
     * public-facing TRS contract that the account is attempting to perform a bulk withdrawal of
     * funds from.
     *
     * <p>conditions: the TRS contract must be live in order to bulk withdraw funds from it. The
     * caller must be the owner of the contract.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x4</b> - refunds a specified amount of depositor's balance for the public-
     * facing TRS contract. [<32b - contractAddress> | <32b - accountAddress> | <128b - amount>]
     * total = 193 bytes where: contractAddress is the address of the public-facing TRS contract
     * that the account given by the address accountAddress is being refunded from. The proposed
     * refund amount is given by amount. The contract interprets the 128 bytes of amount as unsigned
     * and strictly positive.
     *
     * <p>conditions: the calling account must be the owner of the public-facing TRS contract. The
     * account accountAddress must have a deposit balannce into this TRS contract of at least
     * amount. The refund operation is enabled only when the contract is unlocked; once locked
     * refunding is disabled. If any of these conditions are not satisfied this method effectively
     * does nothing. If a contract's funds are open then this method is disabled.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x5</b> - deposits the specified amount of funds into the contract on the
     * behalf of the specified account. This operation is here so that, if direct deposits into the
     * contract are disabled, the contract owner has a means of depositing funds into the contract
     * for the users.
     *
     * <p>[<32b - contractAddress> | <32b - forAccount> | <128b - amount>] total = 193 bytes where:
     * contractAddress is the address of the public-facing TRS contract. forAccount is the account
     * that will be listed as having amount deposited into it if the operation succeeds. amount is
     * the amount of funds to deposit into the contract on forAccount's behalf.
     *
     * <p>conditions: the TRS contract must not yet be locked (or obviously live) and the caller
     * must be the contract owner. If a contract's funds are open then this operation is disabled.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x6</b> - adds extra funds to the TRS contract that are split proportionally
     * among the contract's depositors according to the proportions each of them has contributed
     * compared to the total amount of user deposits (that is, excluding added extra funds and bonus
     * amounts). These extra funds are withdrawn by the depositors equally over the number of
     * remaining periods.
     *
     * <p>[<32b - contractAddress> | <128b - amount>] total = 161 bytes where: contractAddress is
     * the address of the public-facing TRS contract. amount is the amount of extra funds to add to
     * the contract.
     *
     * <p>conditions: the caller must be the contract owner. If a contract's funds are open then
     * this operation is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of calling execute on the specified input.
     */
    @Override
    public TransactionResult execute(byte[] input, long nrgLimit) {
        if (input == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }
        if (input.length == 0) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }
        if (nrgLimit < COST) {
            return new TransactionResult(ResultCode.OUT_OF_ENERGY, 0);
        }
        if (!isValidTxNrg(nrgLimit)) {
            return new TransactionResult(ResultCode.INVALID_ENERGY_LIMIT, 0);
        }

        int operation = input[0];
        switch (operation) {
            case 0:
                return deposit(input, nrgLimit);
            case 1:
                return withdraw(input, nrgLimit);
            case 2:
                return bulkDepositFor(input, nrgLimit);
            case 3:
                return bulkWithdraw(input, nrgLimit);
            case 4:
                return refund(input, nrgLimit);
            case 5:
                return depositFor(input, nrgLimit);
            case 6:
                return addExtraFunds(input, nrgLimit);
            default:
                return new TransactionResult(ResultCode.FAILURE, 0);
        }
    }

    /**
     * Logic to deposit funds to an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x0> | <32b - contractAddress> |
     * <128b - amount>] total = 161 bytes where: contractAddress is the address of the public-facing
     * TRS contract to deposit funds into. amount is the amount of funds to deposit. The contract
     * interprets these 128 bytes as an unsigned and positive amount.
     *
     * <p>conditions: the calling account must have enough balance to deposit amount otherwise this
     * method effectively does nothing. The deposit operation is enabled only when the contract is
     * unlocked; once locked depositing is disabled. Note that if zero is deposited, the call will
     * succeed but the depositor will not be saved into the database. If a contract has its funds
     * open then depositing is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input to deposit to a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult deposit(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int indexAmount = 33;
        final int len = 161;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, indexAmount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A deposit operation can only execute if direct depositing is enabled or caller is owner.
        AionAddress owner = getContractOwner(contract);
        if (!caller.equals(owner) && !isDirDepositsEnabled(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A deposit operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity) and funds are not
        // open.
        if (isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The caller must have adequate funds to make the proposed deposit.
        BigInteger fundsAvailable = track.getBalance(caller);
        if (fundsAvailable.compareTo(amount) < 0) {
            return new TransactionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        TransactionResult result = makeDeposit(contract, caller, amount, nrgLimit);
        if (result.getResultCode().equals(ResultCode.SUCCESS)) {
            track.flush();
        }
        return result;
    }

    /**
     * Logic to withdraw funds from an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<32b - contractAddress>] total = 33
     * bytes where: contractAddress is the address of the public-facing TRS contract that the
     * account is attempting to withdraw funds from.
     *
     * <p>conditions: the TRS contract must be live in order to withdraw funds from it.
     *
     * <p>returns: void.
     *
     * @param input The input to withdraw from a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult withdraw(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A withdraw operation can only execute if the current state of the TRS contract is:
        // contract is live (and obviously locked -- check this for sanity) or contract funds are
        // open.
        if (!isOpenFunds(contract) && (!isContractLocked(contract) || !isContractLive(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        if (!makeWithdrawal(contract, caller)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        track.flush();
        return new TransactionResult(ResultCode.SUCCESS, COST - nrgLimit);
    }

    /**
     * Logic to bulk-deposit-for funds into an existing public-facing TRS contract.
     *
     * <p>Define "entry" as the following 160b array: [<32b - accountAddress> | <128b - amount>]
     * Then the arguments to this operation are: [ <1b - 0x2> | <32b - contractAddress> | E] where E
     * is a contiguous list of X 160-byte entry arrays, such that X is in [1, 100] total =
     * 193-16,033 bytes where: contractAddress is the address of the public-facing TRS contract that
     * the account is attempting to deposit funds into. and for each entry we have the following
     * pair: accountAddress is the address of an account the caller is attempting to deposit funds
     * into the contract for. amount is the corresponding amount of funds to deposit on behalf of
     * accountAddress.
     *
     * <p>conditions: the TRS contract must not yet be locked (nor obviously live) to bulkDeposit
     * into it. The caller must be the owner of the contract. Each accountAddress must be an Aion
     * account address. If a contract has its funds open then this operation is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input to bulk-deposit-for into a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult bulkDepositFor(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int indexEntries = 33;
        final int entryLen = 160;
        final int entryAddrLen = AionAddress.SIZE;
        final int maxEntries = 100;

        // First ensure some basic properties about input hold: its lower and upper size limits and
        // that the entries portion has a length that is a multiple of an entry length.
        int len = input.length;
        if ((len < indexEntries + entryLen) || (len > indexEntries + (entryLen * maxEntries))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        } else if ((len - indexEntries) % entryLen != 0) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress
            .wrap(Arrays.copyOfRange(input, indexContract, indexEntries));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A bulk-deposit-for operation can only execute if the caller is the owner of the contract.
        if (!getContractOwner(contract).equals(caller)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A bulkDepositFor operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity) and funds are not
        // open.
        if (isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Iterate over every entry in the entries list and attempt to deposit for them.
        int numEntries = (len - indexEntries) / entryLen;
        int amtLen = entryLen - entryAddrLen;
        int index = indexEntries;
        byte[] amountBytes;
        AionAddress[] beneficiaries = new AionAddress[numEntries];
        BigInteger[] amounts = new BigInteger[numEntries];
        for (int i = 0; i < numEntries; i++) {
            // Put amount in a byte array one byte larger with an empty initial byte so it is
            // unsigned.
            amountBytes = new byte[amtLen + 1];
            System.arraycopy(input, index + entryAddrLen, amountBytes, 1, amtLen);
            amounts[i] = new BigInteger(amountBytes);

            // Verify the account is an Aion address.
            if (input[index] != AION_PREFIX) {
                return new TransactionResult(ResultCode.FAILURE, 0);
            }

            beneficiaries[i] = AionAddress.wrap(Arrays.copyOfRange(input, index, index + entryAddrLen));
            index += 32 + 128;
        }

        BigInteger totalAmounts = BigInteger.ZERO;
        for (BigInteger amt : amounts) {
            totalAmounts = totalAmounts.add(amt);
        }

        if (track.getBalance(caller).compareTo(totalAmounts) < 0) {
            return new TransactionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        for (int i = 0; i < numEntries; i++) {
            makeDeposit(contract, beneficiaries[i], amounts[i], nrgLimit);
        }

        track.flush();
        return new TransactionResult(ResultCode.SUCCESS, COST - nrgLimit);
    }

    /**
     * Logic to bulk-withdraw funds from an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<32b - contractAddress>] total = 33
     * bytes where: contractAddress is the address of the public-facing TRS contract that the
     * account is attempting to perform a bulk withdrawal of funds from.
     *
     * <p>conditions: the TRS contract must be live in order to bulk withdraw funds from it. The
     * caller must be the owner of the contract.
     *
     * <p>returns: void.
     *
     * @param input The input to bulk-withdraw from a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult bulkWithdraw(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A bulk-withdraw operation can only execute if the caller is the owner of the contract.
        if (!getContractOwner(contract).equals(caller)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A bulk-withdraw operation can only execute if the current state of the TRS contract is:
        // contract is live (and obviously locked -- check this for sanity) or the funds are open.
        if (!isOpenFunds(contract) && (!isContractLocked(contract) || !isContractLive(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Iterate over all depositors and withdraw on their behalf. Once here this is a success.
        byte[] curr = getListHead(contract);
        if (curr == null) {
            return new TransactionResult(ResultCode.SUCCESS, 0);
        }

        while (curr != null) {
            curr[0] = AION_PREFIX;
            AionAddress currAcct = new AionAddress(curr);
            makeWithdrawal(contract, currAcct);
            curr = getListNext(contract, currAcct);
        }

        track.flush();
        return new TransactionResult(ResultCode.SUCCESS, COST - nrgLimit);
    }

    /**
     * Logic to refund funds for an account in an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x4> | <32b - contractAddress> |
     * <32b - accountAddress> | <128b - amount>] total = 193 bytes where: contractAddress is the
     * address of the public-facing TRS contract that the account given by the address
     * accountAddress is being refunded from. The proposed refund amount is given by amount. The
     * contract interprets the 128 bytes of amount as unsigned and strictly positive.
     *
     * <p>conditions: the calling account must be the owner of the public-facing TRS contract. The
     * account accountAddress must have a deposit balannce into this TRS contract of at least
     * amount. The refund operation is enabled only when the contract is unlocked; once locked
     * refunding is disabled. If any of these conditions are not satisfied this method effectively
     * does nothing. If a contract has its funds open then this operation is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input to refund an account for a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult refund(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int indexAccount = 33;
        final int indexAmount = 65;
        final int len = 193;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress
            .wrap(Arrays.copyOfRange(input, indexContract, indexAccount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A refund operation can only execute if the caller is the contract owner.
        AionAddress owner = getContractOwner(contract);
        if (!caller.equals(owner)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A refund operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity) and funds are not
        // open.
        if (isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Ensure the account exists (ie. has a positive deposit balance for the contract).
        AionAddress account = AionAddress.wrap(Arrays.copyOfRange(input, indexAccount, indexAmount));
        BigInteger accountBalance = getDepositBalance(contract, account);
        if (accountBalance.equals(BigInteger.ZERO)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The account must have a deposit balance large enough to make the refund.
        BigInteger newBalance = accountBalance.subtract(amount);
        if (newBalance.compareTo(BigInteger.ZERO) < 0) {
            return new TransactionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        // If refund amount is larger than zero, update the depositor's current deposit balance and
        // then update the deposit meta-data (linked list, count, etc.) and refund the account.
        if (amount.compareTo(BigInteger.ZERO) > 0) {
            if (!setDepositBalance(contract, account, newBalance)) {
                return new TransactionResult(ResultCode.FAILURE, 0);
            }
            setTotalBalance(contract, getTotalBalance(contract).subtract(amount));

            // If the account's balance drops to zero, delete account from the TRS contract.
            if (newBalance.equals(BigInteger.ZERO)) {
                listRemoveAccount(contract, account);
            }

            track.addBalance(account, amount);
            track.flush();
        }
        return new TransactionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Logic to deposit funds on the behalf of an account in an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x5> | <32b - contractAddress> |
     * <32b - forAccount> | <128b - amount>] total = 193 bytes where: contractAddress is the address
     * of the public-facing TRS contract. forAccount is the account that will be listed as having
     * amount deposited into it if the operation succeeds. amount is the amount of funds to deposit
     * into the contract on forAccount's behalf. This value is interpreted as unsigned and strictly
     * positive.
     *
     * <p>conditions: the TRS contract must not yet be locked (or obviously live) and the caller
     * must be the contract owner. If a contract has its funds open then this operation is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input to deposit on the behalf of an account for a public-facing TRS
     *     contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult depositFor(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int indexAccount = 33;
        final int indexAmount = 65;
        final int len = 193;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress
            .wrap(Arrays.copyOfRange(input, indexContract, indexAccount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A depositFor operation can only execute if caller is owner.
        if (!caller.equals(getContractOwner(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A depositFor operation can only execute if the current state of the TRS contract is:
        // contract is unlocked (and obviously not live -- check this for sanity) and funds are not
        // open.
        if (isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The caller must have adequate funds to make the proposed deposit.
        BigInteger fundsAvailable = track.getBalance(caller);
        if (fundsAvailable.compareTo(amount) < 0) {
            return new TransactionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        // Verify the account is an Aion address.
        if (input[indexAccount] != AION_PREFIX) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress account = AionAddress.wrap(Arrays.copyOfRange(input, indexAccount, indexAmount));
        TransactionResult result = makeDeposit(contract, account, amount, nrgLimit);
        if (result.getResultCode().equals(ResultCode.SUCCESS)) {
            track.flush();
        }
        return result;
    }

    /**
     * Logic to add extra funds to an existing public-facing TRS contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x6> | <32b - contractAddress> |
     * <128b - amount>] total = 161 bytes where: contractAddress is the address of the public-facing
     * TRS contract. amount is the amount of extra funds to add to the contract.
     *
     * <p>conditions: the caller must be the contract owner. If a contract's funds are open then
     * this operation is disabled.
     *
     * <p>returns: void.
     *
     * @param input The input to add extra funds into a public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult addExtraFunds(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int indexAmount = 33;
        final int len = 161;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexContract, indexAmount));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A depositFor operation can only execute if caller is owner.
        if (!caller.equals(getContractOwner(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // If contract has its funds open then this operation fails.
        if (isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Put amount in a byte array one byte larger with an empty initial byte so it is unsigned.
        byte[] amountBytes = new byte[len - indexAmount + 1];
        System.arraycopy(input, indexAmount, amountBytes, 1, len - indexAmount);
        BigInteger amount = new BigInteger(amountBytes);

        // The caller must have adequate funds to make the proposed deposit.
        BigInteger fundsAvailable = track.getBalance(caller);
        if (fundsAvailable.compareTo(amount) < 0) {
            return new TransactionResult(ResultCode.INSUFFICIENT_BALANCE, 0);
        }

        if (amount.compareTo(BigInteger.ZERO) > 0) {
            setExtraFunds(contract, getExtraFunds(contract).add(amount));
            track.addBalance(caller, amount.negate());
            return new TransactionResult(ResultCode.SUCCESS, COST - nrgLimit);
        }
        return new TransactionResult(ResultCode.FAILURE, 0);
    }

    // <-------------------------------------HELPER METHODS---------------------------------------->

    /**
     * Deposits amount into the TRS contract whose address is contract on behalf of account. If
     * amount is zero then this method does nothing but return a successful execution result.
     *
     * <p>This method transfers amount from the caller's balance into the contract. It does not take
     * this amount from account (unless account is the caller). However, if this method succeeds
     * then account will have a deposit balance in the contract of amount amount.
     *
     * <p>The reason the funds are removed from the caller's account and placed into the contract on
     * behalf of account is to faciliate both the deposit and depositFor functionalities.
     *
     * @param contract the TRS contract to update.
     * @param account The beneficiary of the deposit.
     * @param amount The amount to be deposited into the contract on behalf of account.
     * @param nrgLimit The energy limit.
     * @return an execution result of either success or internal error.
     */
    private TransactionResult makeDeposit(
            AionAddress contract, AionAddress account, BigInteger amount, long nrgLimit) {

        // If deposit amount is larger than zero, update the curret deposit balance of the account
        // for which this deposit is on the behalf of, and update the meta-deta etc.
        if (amount.compareTo(BigInteger.ZERO) > 0) {
            BigInteger currAmount = getDepositBalance(contract, account);
            if (!setDepositBalance(contract, account, currAmount.add(amount))) {
                return new TransactionResult(ResultCode.FAILURE, 0);
            }
            listAddToHead(contract, account);
            setTotalBalance(contract, getTotalBalance(contract).add(amount));
            track.addBalance(caller, amount.negate());
        }
        return new TransactionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Updates the linked list for the TRS contract given by contract, if necessary, in the
     * following way:
     *
     * <p>If the caller does not have a valid account in the TRS contract contract then that account
     * is added to the head of the linked list for that contract.
     *
     * <p>If the account is valid and already exists this method does nothing, since the caller must
     * already be in the list.
     *
     * @param contract The TRS contract to update.
     */
    private void listAddCallerToHead(AionAddress contract) {
        listAddToHead(contract, caller);
    }

    /**
     * Updates the linked list for the TRS contract given by contract, if necessary, in the
     * following way:
     *
     * <p>If account does not have a valid account in the TRS contract contract then that account is
     * added to the head of the linked list for that contract.
     *
     * <p>If the account is valid and already exists this method does nothing, since the account
     * must already be in the list.
     *
     * @param contract The TRS contract to update.
     */
    private void listAddToHead(AionAddress contract, AionAddress account) {
        byte[] next = getListNextBytes(contract, account);
        if (accountIsValid(next)) {
            return;
        } // no update needed.

        // Set account's next entry to point to head.
        byte[] head = getListHead(contract);
        setListNext(contract, account, next[0], head, true);

        // If head was non-null set the head's previous entry to point to account.
        if (head != null) {
            head[0] = AION_PREFIX;
            setListPrevious(
                    contract,
                    AionAddress.wrap(head),
                    Arrays.copyOf(account.toBytes(), AionAddress.SIZE));
        }

        // Set the head of the list to point to account and set account's previous entry to null.
        setListHead(contract, Arrays.copyOf(account.toBytes(), AionAddress.SIZE));
        setListPrevious(contract, account, null);
    }

    /**
     * Updates the linked list for the TRS contract given by contract, if necessary, in the
     * following way:
     *
     * <p>If the account is valid and exists in contract then that account is removed from the
     * linked list for contract and the account is marked as invalid since we cannot have valid
     * accounts that are not in the list.
     *
     * <p>If the account is not valid for the TRS contract this method does nothing, since the
     * account must not be in the list anyway.
     *
     * @param contract The TRS contract to update.
     * @param account The account to remove from the list.
     */
    private void listRemoveAccount(AionAddress contract, AionAddress account) {
        byte[] prev = getListPrev(contract, account);
        byte[] next = getListNext(contract, account);

        // If account is head of list, set head to account's next entry.
        // TODO: maybe decouple metadata from next so we dont have to do awkward things like this?
        byte[] head = getListHead(contract);
        head[0] = AION_PREFIX;
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
