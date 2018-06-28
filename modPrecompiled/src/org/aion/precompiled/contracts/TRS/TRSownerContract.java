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
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;

/**
 * The TRSownerContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since the
 * TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * The TRSownerContract is the component of the public-facing TRS contract that the contract owner
 * interacts with primarily. The operations provided here are all operations that only the contract
 * owner can use and are related to the overall integrity of the contract itself.
 *
 * The following operations are supported:
 *      create -- creates a new public TRS contract.
 *      lock -- locks the TRS contract so that no more deposits may be made.
 *      start -- starts the distribution of the savings in the TRS contract.
 *      nullify -- disables the TRS contract.
 *      mint -- informs the TRS contract about tokens that were minted to it on behalf of a depositor.
 */
public final class TRSownerContract extends AbstractTRS {
    private static final long COST = 21000L;    // temporary.

    /**
     * Constructs a new TRSownerContract that will use track as the database cache to update its
     * state with and is called by caller.
     *
     * @param track The database cache.
     * @param caller The calling address.
     * @throws NullPointerException if track or caller are null.
     */
    public TRSownerContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address caller) {

        super(track, caller);
    }

    /**
     * The input byte array provided to this method must have the following format:
     *
     * [<1b - operation> | <arguments>]
     *
     * where arguments is defined differently for different operations. The supported operations
     * along with their expected arguments are outlined as follows:
     *
     *   <b>operation 0x0</b> - creates a new public-facing TRS contract.
     *     [<1b - isDirectDeposit> | <2b - periods> | <9b - percent> | <1b - precision>]
     *     total = 14 bytes
     *   where:
     *     isDirectDeposit is 0x0 if direct depositing is disabled for this TRS
     *     isDirectDeposit is 0x1 if direct depositing is enabled for this TRS
     *     all other values of isDirectDeposit are invalid.
     *     periods is the number of 30-day withdrawal periods for this TRS (unsigned).
     *     percent is the percentage of the total savings that are withdrawable during the one-off
     *       special withdraw event.
     *     precision is the number of decimal places to left-shift percent in order to achieve
     *       greater precision.
     *
     *     All input values interpreted as numbers will be interpreted as unsigned and positive.
     *
     *     conditions: precision must be in the range [0, 18] and after the shifts have been applied
     *       to percent the resulting percentage must be in the range [0, 1]. periods must be in the
     *       range [1, 1200].
     *
     *     returns: the address of the newly created TRS contract in the output field of the
     *       ContractExecutionResult.
     *
     *                                          ~~~***~~~
     *
     *     <b>operation 0x1</b> - locks a public-facing TRS contract. Once a contract is locked no
     *       more deposits can be made into it and ability to refund a participant is disabled.
     *       [<32b - contractAddress>]
     *       total = 33 bytes
     *     where:
     *       contractAddress is the address of the public-facing TRS contract to lock.
     *
     *     conditions: the caller of this method must be the owner of the specified contract
     *       otherwise this method will fail.
     *
     *     returns: void.
     *
     *                                            ~~~***~~~
     *
     *     <b>operation 0x2</b> - starts a public-facing TRS contract. Once a contract starts it is
     *       considered live and the first withdrawal period has officially begun. A contract must
     *       first be locked before it can be made live.
     *       [<32b - contractAddress>]
     *       total = 33 bytes
     *     where:
     *       contractAddress is the address of the public-facing TRS contract to start.
     *
     *     conditions: the caller of this method must be the owner of the specified contract
     *       otherwise this method will fail.
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
            case 0: return create(input, nrgLimit);
            case 1: return lock(input, nrgLimit);
            case 2: return start(input, nrgLimit);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Logic to create a new public-facing TRS contract where caller is the owner of the contract.
     *
     * The input byte array format is defined as follows:
     *   [<1b - 0x0> | <1b - isDirectDeposit> | <2b - periods> | <9b - percent> | <1b - precision>]
     *   total = 14 bytes
     * where:
     *   isDirectDeposit is 0x0 if direct depositing is disabled for this TRS
     *   isDirectDeposit is 0x1 if direct depositing is enabled for this TRS
     *   isDirectDeposit is 0x2 to create a public-facing TRS contract for testing with direct
     *     depositing disabled. (Aion-only).
     *   isDirectDeposit is 0x3 to create a public-facing TRS contract for testing with direct
     *     depositing enabled. (Aion-only).
     *   all other values of isDirectDeposit are invalid.
     *   periods is the number of 30-day withdrawal periods for this TRS (unsigned).
     *   percent is the percentage of the total savings that are withdrawable during the one-off
     *     special withdraw event.
     *   precision is the number of decimal places to left-shift percent in order to achieve
     *     greater precision.
     *
     *   All input values interpreted as numbers will be interpreted as unsigned and positive.
     *
     *   conditions: precision must be in the range [0, 18] and after the shifts have been applied
     *     to percent the resulting percentage must be in the range [0, 1]. periods must be in the
     *     range [1, 1200].
     *
     *   Note: a TRS contract created for testing will set its period duration to 30 SECONDS instead
     *   of days. Nothing else about the contract will be different.
     *
     * @param input The input to the create public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult create(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexDepo = 1;
        final int indexPeriod = 2;
        final int indexPercent = 4;
        final int indexPrecision = 13;
        final int len = 14;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        int deposit = input[indexDepo];
        if (deposit < 0 || deposit > 3) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // If request for a test contract, verify caller is Aion.
        boolean isTestContract = (deposit == 2 || deposit == 3);
        if (isTestContract && !caller.equals(AION)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        boolean isDirectDeposit = (deposit == 1 || deposit == 3);

        // Grab the requested number of periods for this contract and verify range. Since 2 byte
        // representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int periods = input[indexPeriod];
        periods <<= Byte.SIZE;
        periods |= (input[indexPeriod + 1] & 0xFF);
        if (periods < 1 || periods > 1200) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Grab the precision and percentage and perform the shiftings and verify the range.
        // 2 byte representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int precision = input[indexPrecision];
        if (precision < 0 || precision > 18) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Keep first byte of percentBytes unused (as zero) so result is always unsigned.
        byte[] percentBytes = new byte[indexPrecision - indexPercent + 1];
        System.arraycopy(input, indexPercent, percentBytes, 1,  indexPrecision - indexPercent);
        percentBytes[0] = (byte) 0x0;   // sanity
        BigInteger percent = new BigInteger(percentBytes);
        BigDecimal realPercent = new BigDecimal(percent).movePointLeft(precision);
        if (realPercent.compareTo(BigDecimal.ZERO) < 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        } else if (realPercent.compareTo(new BigDecimal("100")) > 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // All checks OK. Generate contract address, save the new contract & return to caller.
        byte[] ownerNonce = track.getNonce(caller).toByteArray();
        byte[] hashInfo = new byte[ownerNonce.length + Address.ADDRESS_LEN];
        System.arraycopy(ownerNonce, 0, hashInfo, 0, ownerNonce.length);
        System.arraycopy(caller.toBytes(), 0, hashInfo, ownerNonce.length, Address.ADDRESS_LEN);
        byte[] trsAddr = HashUtil.h256(hashInfo);
        trsAddr[0] = TRS_PREFIX;
        Address contract = new Address(trsAddr);

        saveNewContract(contract, isTestContract, isDirectDeposit, periods, percent, precision);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST, contract.toBytes());
    }

    /**
     * Logic to lock an existing public-facing TRS contract where caller is the owner of the contract.
     *
     * The input byte array format is defined as follows:
     *   [<32b - contractAddress>]
     *   total = 33 bytes
     * where:
     *   contractAddress is the address of the public-facing TRS contract to lock.
     *
     * conditions: the caller of this method must be the owner of the specified contract
     *   otherwise this method will fail.
     *
     * @param input The input to the lock public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult lock(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddr = 1;
        final int len = 33;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // The caller must also be the owner of this contract.
        Address contract = new Address(Arrays.copyOfRange(input, indexAddr, indexAddr + Address.ADDRESS_LEN));
        if (!caller.equals(fetchContractOwner(contract))) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A lock call can only execute if the current state of the TRS contract is as follows:
        // contract is unlocked & contract is not live.
        IDataWord specs = fetchContractSpecs(contract);
        if (specs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] specBytes = specs.getData();
        if (isContractLocked(specBytes) || isContractLive(specBytes)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // All checks OK. Change contract state to locked.
        setLock(contract);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    /**
     * Logic to start an existing public-facing TRS contract where caller is the owner of the contract.
     *
     * The input byte array format is defined as follows:
     *   [<32b - contractAddress>]
     *   total = 33 bytes
     * where:
     *   contractAddress is the address of the public-facing TRS contract to lock.
     *
     * conditions: the caller of this method must be the owner of the specified contract
     *   otherwise this method will fail.
     *
     * @param input The input to the lock public-facing TRS contract logic.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult start(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddr = 1;
        final int len = 33;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // The caller must also be the owner of this contract.
        Address contract = new Address(Arrays.copyOfRange(input, indexAddr, indexAddr + Address.ADDRESS_LEN));
        if (!caller.equals(fetchContractOwner(contract))) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // A start call can only execute if the current state of the TRS contract is as follows:
        // contract is locked & contract is not live.
        IDataWord specs = fetchContractSpecs(contract);
        if (specs == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] specBytes = specs.getData();
        if (!isContractLocked(specBytes) || isContractLive(specBytes)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // All checks OK. Change contract state to live.
        setLive(contract);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST);
    }

    // <-----------------------DATABASE GET, SET, QUERY METHODS BELOW------------------------------>

    /**
     * Saves a new public-facing TRS contract whose contract address is contract and whose direct
     * deposit option is isDirectDeposit. The new contract will have periods number of withdrawable
     * periods and the percentage that is derived from percent and precision as outlined in create()
     * is the percentage of total funds withdrawable in the one-off special event. The isTest
     * parameter tells this method whether to save the new contract as a test contract (using 30
     * second periods) or not (using 30 day periods). The owner of the contract is caller.
     *
     * All preconditions on the parameters are assumed already verified.
     *
     * The meta-data for the contract is stored in the database using 16-byte words as follows:
     *   key for first half of owner address: 80 ...
     *   key for second half of owner address: 80 ... 01
     *   key for contract specs: C0 ...
     *
     * where ... means all unmentioned bits replaced by this ellipsis are 0.
     *
     * The 16-byte value for the contract specs is formatted as follows:
     *   | 9b - percentage | 1b - isTest | 1b - isDirectDeposit | 1b - precision | 2b - periods |
     *   | 1b - isLocked | 1b - isLive |
     *
     * @param contract The address of the new TRS contract.
     * @param isTest True only if this new TRS contract is a test contract.
     * @param isDirectDeposit True only if direct depositing is enabled for this TRS contract.
     * @param periods The number of withdrawable periods this TRS contract is live for.
     * @param percent The percent of total funds withdrawable in the one-off event.
     * @param precision The number of decimal places to put the decimal point in percent.
     */
    private void saveNewContract(Address contract, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        // First, save the "specifications" meta-data for this contract.

        // "constants"
        final int indexTest = 9;    // note: indexTest == length of percent
        final int indexDepo = 10;
        final int indexPrecision = 11;
        final int indexPeriods = 12;

        byte[] specKey = new byte[DataWord.BYTES];
        specKey[0] = SPECS_CODE;

        byte[] specValue = new byte[DataWord.BYTES];
        byte[] percentBytes = percent.toByteArray();
        System.arraycopy(percentBytes, 0, specValue, indexTest - percentBytes.length,
            percentBytes.length);
        specValue[indexTest] = (isTest) ? (byte) 0x1 : (byte) 0x0;
        specValue[indexDepo] = (isDirectDeposit) ? (byte) 0x1 : (byte) 0x0;
        specValue[indexPrecision] = (byte) precision;
        specValue[indexPeriods] = (byte) ((periods >> Byte.SIZE) & 0xFF);
        specValue[indexPeriods + 1] = (byte) (periods & 0xFF);
        specValue[LOCK_OFFSET] = (byte) 0x0; // sanity
        specValue[LIVE_OFFSET] = (byte) 0x0; // sanity

        track.createAccount(contract);
        saveContractOwner(contract);
        track.addStorageRow(contract, new DataWord(specKey), new DataWord(specValue));

        // Second, save the deposits meta-data for this contract.
        byte[] depositsKey = new byte[DataWord.BYTES];
        depositsKey[0] = DEPOSITS_CODE;

        // Last 64 bits of value is the pointer to the head of the list. Set the leftmost of these
        // 64 bits to 1 to indicate 'null' or no head, since no deposits yet.
        byte[] depositsValue = new byte[DataWord.BYTES];
        depositsValue[DEPOSITS_ADDR_SPACE] = (byte) 0x80;

        track.addStorageRow(contract, new DataWord(depositsKey), new DataWord(depositsValue));
        track.flush();
    }

    /**
     * Saves the owner of the contract into the contract address. Since our key-value pairs in the
     * database are 16 byte words we must store the owner address in two key-value pairs.
     *
     * The key for the first half of the address has all its bits set to 0 except for the most
     *   significant bit, which is 1.
     *
     * The key for the second half of the address has all its bits set to 0 except for the most and
     *   least significant bits, both of which are 1.
     *
     * @param contract The contract to save the owner address to.
     */
    private void saveContractOwner(Address contract) {
        byte[] addr1 = Arrays.copyOfRange(caller.toBytes(), 0, DataWord.BYTES);
        byte[] addr2 = Arrays.copyOfRange(caller.toBytes(), DataWord.BYTES, Address.ADDRESS_LEN);

        byte[] key1 = new byte[DataWord.BYTES];
        key1[0] = OWNER_CODE;
        track.addStorageRow(contract, new DataWord(key1), new DataWord(addr1));

        byte[] key2 = new byte[DataWord.BYTES];
        key2[0] = OWNER_CODE;
        key2[DataWord.BYTES - 1] = (byte) 0x01;
        track.addStorageRow(contract, new DataWord(key2), new DataWord(addr2));
    }

    /**
     * Returns the owner of the TRS contract whose address is contract if contract is a valid
     * contract address.
     *
     * Returns null if contract is not a valid TRS contract address and thus there is no owner to
     * fetch.
     *
     * @param contract The TRS contract address.
     * @return the owner of the contract or null if not a TRS contract.
     */
    private Address fetchContractOwner(Address contract) {
        if (contract.toBytes()[0] != TRS_PREFIX) { return null; }
        byte[] owner = new byte[Address.ADDRESS_LEN];

        byte[] key1 = new byte[DataWord.BYTES];
        key1[0] = OWNER_CODE;
        IDataWord half1 = track.getStorageValue(contract, new DataWord(key1));
        if (half1 == null) { return null; }
        System.arraycopy(half1.getData(), 0, owner, 0, DataWord.BYTES);

        byte[] key2 = new byte[DataWord.BYTES];
        key2[0] = OWNER_CODE;
        key2[DataWord.BYTES - 1] = (byte) 0x01;
        IDataWord half2 = track.getStorageValue(contract, new DataWord(key2));
        if (half2 == null) { return null; }
        System.arraycopy(half2.getData(), 0, owner, DataWord.BYTES, DataWord.BYTES);

        return new Address(owner);
    }

    /**
     * Updates the specifications associated with the TRS contract whose address is contract so that
     * the is-locked bit will be set. If the bit is already set this method effectively does nothing.
     *
     * Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    private void setLock(Address contract) {
        byte[] specKey = new byte[DataWord.BYTES];
        specKey[0] = SPECS_CODE;

        byte[] specValue = fetchContractSpecs(contract).getData();
        specValue[LOCK_OFFSET] = (byte) 0x1;

        track.addStorageRow(contract, new DataWord(specKey), new DataWord(specValue));
        track.flush();
    }

    /**
     * Updates the specifications associated with the TRS contract whose address is contract so that
     * the is-live bit will be set. If the bit is already set this method effectively does nothing.
     *
     * Assumption: contract IS a valid TRS contract address.
     *
     * @param contract The address of the TRS contract.
     */
    private void setLive(Address contract) {
        byte[] specKey = new byte[DataWord.BYTES];
        specKey[0] = SPECS_CODE;

        byte[] specValue = fetchContractSpecs(contract).getData();
        specValue[LIVE_OFFSET] = (byte) 0x1;

        track.addStorageRow(contract, new DataWord(specKey), new DataWord(specValue));
        track.flush();
    }

}