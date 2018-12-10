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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.AionAddress;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.base.vm.IDataWord;

/**
 * The TRSstateContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * <p>The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since
 * the TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * <p>The TRSstateContract is the component of the public-facing TRS contract that the contract
 * owner interacts with primarily. The operations provided here are all operations that only the
 * contract owner can use and are related to the overall integrity of the contract itself.
 *
 * <p>The following operations are supported: create -- creates a new public TRS contract. lock --
 * locks the TRS contract so that no more deposits may be made. start -- starts the distribution of
 * the savings in the TRS contract. openFunds -- kills the TRS contract and unlocks all of the funds
 * for depositors to withdraw.
 */
public final class TRSstateContract extends AbstractTRS {

    /**
     * Constructs a new TRSstateContract that will use track as the database cache to update its
     * state with and is called by caller.
     *
     * @param track The database cache.
     * @param caller The calling address.
     * @throws NullPointerException if track or caller are null.
     */
    public TRSstateContract(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            AionAddress caller,
            IBlockchain blockchain) {

        super(track, caller, blockchain);
    }

    /**
     * The input byte array provided to this method must have the following format:
     *
     * <p>[<1b - operation> | <arguments>]
     *
     * <p>where arguments is defined differently for different operations. The supported operations
     * along with their expected arguments are outlined as follows:
     *
     * <p><b>operation 0x0</b> - creates a new public-facing TRS contract. [<1b - isDirectDeposit> |
     * <2b - periods> | <9b - percent> | <1b - precision>] total = 14 bytes where: isDirectDeposit
     * is 0x0 if direct depositing is disabled for this TRS isDirectDeposit is 0x1 if direct
     * depositing is enabled for this TRS all other values of isDirectDeposit are invalid. periods
     * is the number of 30-day withdrawal periods for this TRS (unsigned). percent is the percentage
     * of the total savings that are withdrawable during the one-off special withdraw event.
     * precision is the number of decimal places to left-shift percent in order to achieve greater
     * precision.
     *
     * <p>All input values interpreted as numbers will be interpreted as unsigned and positive.
     *
     * <p>conditions: precision must be in the range [0, 18] and after the shifts have been applied
     * to percent the resulting percentage must be in the range [0, 1]. periods must be in the range
     * [1, 1200].
     *
     * <p>returns: the address of the newly created TRS contract in the output field of the
     * ExecutionResult.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x1</b> - locks a public-facing TRS contract. Once a contract is locked no
     * more deposits can be made into it and ability to refund a participant is disabled. [<32b -
     * contractAddress>] total = 33 bytes where: contractAddress is the address of the public-facing
     * TRS contract to lock.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. A contract cannot be locked until the total amount of funds
     * deposited into the contract is a strictly positive number. A contract cannot be locked if the
     * contract's funds are open.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x2</b> - starts a public-facing TRS contract. Once a contract starts it is
     * considered live and the first withdrawal period has officially begun. A contract must first
     * be locked before it can be made live. [<32b - contractAddress>] total = 33 bytes where:
     * contractAddress is the address of the public-facing TRS contract to start.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. A contract cannot be started if its funds are open.
     *
     * <p>returns: void.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x3</b> - kills the public-facing TRS contract so that it can never again be
     * locked or made live and unlocks all of the funds in it so that any account that has a
     * positive deposit balance in the contract is freely able to withdraw all of their funds using
     * one withdraw operation (bulkWithdraw will also produce the same effect). [<32b -
     * contractAddress>] total = 33 bytes where: contractAddress is the address of the public-facing
     * TRS contract to open up.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. The contract must not already be live.
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
                return create(input, nrgLimit);
            case 1:
                return lock(input, nrgLimit);
            case 2:
                return start(input, nrgLimit);
            case 3:
                return openFunds(input, nrgLimit);
            default:
                return new TransactionResult(ResultCode.FAILURE, 0);
        }
    }

    /**
     * Logic to create a new public-facing TRS contract where caller is the owner of the contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x0> | <1b - isDirectDeposit> |
     * <2b - periods> | <9b - percent> | <1b - precision>] total = 14 bytes where: isDirectDeposit
     * is 0x0 if direct depositing is disabled for this TRS isDirectDeposit is 0x1 if direct
     * depositing is enabled for this TRS isDirectDeposit is 0x2 to create a public-facing TRS
     * contract for testing with direct depositing disabled. (Aion-only). isDirectDeposit is 0x3 to
     * create a public-facing TRS contract for testing with direct depositing enabled. (Aion-only).
     * all other values of isDirectDeposit are invalid. periods is the number of 30-day withdrawal
     * periods for this TRS (unsigned). percent is the percentage of the total savings that are
     * withdrawable during the one-off special withdraw event. precision is the number of decimal
     * places to left-shift percent in order to achieve greater precision.
     *
     * <p>All input values interpreted as numbers will be interpreted as unsigned and positive.
     *
     * <p>conditions: precision must be in the range [0, 18] and after the shifts have been applied
     * to percent the resulting percentage must be in the range [0, 1]. periods must be in the range
     * [1, 1200].
     *
     * <p>Note: a TRS contract created for testing will set its period duration to 30 SECONDS
     * instead of days. Nothing else about the contract will be different.
     *
     * @param input The input to the create public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult create(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexDepo = 1;
        final int indexPeriod = 2;
        final int indexPercent = 4;
        final int indexPrecision = 13;
        final int len = 14;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        int deposit = input[indexDepo];
        if (deposit < 0 || deposit > 3) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // If request for a test contract, verify caller is Aion.
        boolean isTestContract = (deposit == 2 || deposit == 3);
        if (isTestContract && !caller.equals(AION)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        boolean isDirectDeposit = (deposit == 1 || deposit == 3);

        // Grab the requested number of periods for this contract and verify range. Since 2 byte
        // representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int periods = input[indexPeriod];
        periods <<= Byte.SIZE;
        periods |= (input[indexPeriod + 1] & 0xFF);
        if (periods < 1 || periods > 1200) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Grab the precision and percentage and perform the shiftings and verify the range.
        // 2 byte representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int precision = input[indexPrecision];
        if (precision < 0 || precision > 18) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // Keep first byte of percentBytes unused (as zero) so result is always unsigned.
        byte[] percentBytes = new byte[indexPrecision - indexPercent + 1];
        System.arraycopy(input, indexPercent, percentBytes, 1, indexPrecision - indexPercent);
        percentBytes[0] = (byte) 0x0; // sanity
        BigInteger percent = new BigInteger(percentBytes);
        BigDecimal realPercent = new BigDecimal(percent).movePointLeft(precision);
        if (realPercent.compareTo(BigDecimal.ZERO) < 0) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        } else if (realPercent.compareTo(new BigDecimal("100")) > 0) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // All checks OK. Generate contract address, save the new contract & return to caller.
        byte[] ownerNonce = track.getNonce(caller).toByteArray();
        byte[] hashInfo = new byte[ownerNonce.length + AionAddress.SIZE];
        System.arraycopy(ownerNonce, 0, hashInfo, 0, ownerNonce.length);
        System.arraycopy(caller.toBytes(), 0, hashInfo, ownerNonce.length, AionAddress.SIZE);
        byte[] trsAddr = HashUtil.h256(hashInfo);
        trsAddr[0] = TRS_PREFIX;
        AionAddress contract = new AionAddress(trsAddr);

        saveNewContract(contract, isTestContract, isDirectDeposit, periods, percent, precision);
        return new TransactionResult(ResultCode.SUCCESS, nrgLimit - COST, contract.toBytes());
    }

    /**
     * Logic to lock an existing public-facing TRS contract where caller is the owner of the
     * contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x1> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract to
     * lock.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. A contract cannot be locked until the total amount of funds
     * deposited into the contract is a strictly positive number. A contract cannot be locked if its
     * funds are open.
     *
     * @param input The input to the lock public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult lock(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddr = 1;
        final int len = 33;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // The caller must also be the owner of this contract.
        AionAddress contract =
                new AionAddress(Arrays.copyOfRange(input, indexAddr, indexAddr + AionAddress.SIZE));
        if (!caller.equals(getContractOwner(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A lock call can only execute if the current state of the TRS contract is as follows:
        // contract is unlocked & contract is not live.
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A contract must have a strictly positive balance before it can be locked.
        if (getTotalBalance(contract).compareTo(BigInteger.ZERO) <= 0) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A lock call can only execute if the contract is in the following state:
        // contract is not locked and not live and its funds are not open.
        if (isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // All checks OK. Change contract state to locked.
        setLock(contract);
        track.flush();
        return new TransactionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Logic to start an existing public-facing TRS contract where caller is the owner of the
     * contract.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x2> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract to
     * lock.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. A contract can only be started if its funds are not open.
     *
     * @param input The input to the lock public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult start(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddr = 1;
        final int len = 33;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // The caller must also be the owner of this contract.
        AionAddress contract =
                new AionAddress(Arrays.copyOfRange(input, indexAddr, indexAddr + AionAddress.SIZE));
        if (!caller.equals(getContractOwner(contract))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A start call can only execute if the current state of the TRS contract is as follows:
        // contract is locked & contract is not live.
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // A contract can only be started if it is in the following state:
        // the contract is locked and not live and its funds are not open.
        if (!isContractLocked(contract) || isContractLive(contract) || isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // All checks OK. Change contract state to live and save set bonus balance for the contract.
        setLive(contract);
        setBonusBalance(contract);
        track.flush();
        return new TransactionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Logic to open up an existing public-facing TRS contract where caller is the owner of the
     * contract.
     *
     * <p>[<1b - 0x3> | <32b - contractAddress>] total = 33 bytes where: contractAddress is the
     * address of the public-facing TRS contract to open up.
     *
     * <p>conditions: the caller of this method must be the owner of the specified contract
     * otherwise this method will fail. The contract must not already be live.
     *
     * <p>returns: void.
     *
     * @param input The input to the openFunds public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private TransactionResult openFunds(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexContract = 1;
        final int len = 33;

        if (input.length != len) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexContract, len));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // An openFunds operation can only execute if the caller is the owner of the contract.
        if (!getContractOwner(contract).equals(caller)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // An openFunds operation can only execute if the current state of the TRS contract is:
        // contract is not live.
        if (isContractLive(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // An openFunds operation can only execute if the contract does not already have its funds
        // open - this is to protect against re-setting the bonus balance (not necessarily bad?).
        if (isOpenFunds(contract)) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        setIsOpenFunds(contract);
        setBonusBalance(contract);
        return new TransactionResult(ResultCode.SUCCESS, COST - nrgLimit);
    }

    // <------------------------------------HELPER METHODS----------------------------------------->

    /**
     * Saves a new public-facing TRS contract whose contract address is contract and whose direct
     * deposit option is isDirectDeposit. The new contract will have periods number of withdrawable
     * periods and the percentage that is derived from percent and precision as outlined in create()
     * is the percentage of total funds withdrawable in the one-off special event. The isTest
     * parameter tells this method whether to save the new contract as a test contract (using 30
     * second periods) or not (using 30 day periods). The owner of the contract is caller.
     *
     * <p>Assumption: all parameters are non-null.
     *
     * <p>If periods, percent or precision are larger than expected they will each be truncated so
     * that they use the expected number of bytes each.
     *
     * <p>If contract already exists then this method does nothing.
     *
     * @param contract The address of the new TRS contract.
     * @param isTest True only if this new TRS contract is a test contract.
     * @param isDirectDeposit True only if direct depositing is enabled for this TRS contract.
     * @param periods The number of withdrawable periods this TRS contract is live for.
     * @param percent The percent of total funds withdrawable in the one-off event.
     * @param precision The number of decimal places to put the decimal point in percent.
     */
    private void saveNewContract(
            AionAddress contract,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        if (getContractOwner(contract) != null) {
            return;
        } // contract exists already.
        track.createAccount(contract);
        setContractOwner(contract);
        setContractSpecs(contract, isTest, isDirectDeposit, periods, percent, precision);
        setListHead(contract, null);
        setTotalBalance(contract, BigInteger.ZERO);
        setTimestamp(contract, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        initOpenFunds(contract);
        track.flush();
    }
}
