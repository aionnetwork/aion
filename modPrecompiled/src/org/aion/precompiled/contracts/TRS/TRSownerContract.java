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
import org.aion.precompiled.type.StatefulPrecompiledContract;

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
 *      mint -- informs the TRS contract about tokens that were minted to it on behalf of a depositor.
 *      nullify -- disables the TRS contract.
 */
public class TRSownerContract extends StatefulPrecompiledContract {
    // grab AION from CfgAion later
    private static final Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    private static final long COST = 21000L;    // temporary.
    private static final byte TRS_PREFIX = (byte) 0xC0;
    private static final int PERIODS_LEN = 2;
    private static final int PERCENT_LEN = 9;
    private static final int PRECISION_LEN = 1;
    private final Address caller;

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

        super(track);
        if (caller == null) {
            throw new NullPointerException("Construct TRSownerContract with null caller.");
        }
        this.caller = caller;
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
        if (input.length != 2 + PERIODS_LEN + PERCENT_LEN + PRECISION_LEN) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        int deposit = input[1];
        if (deposit < 0 || deposit > 3) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // If request for a test contract, verify caller is Aion.
        boolean isTestContract = (deposit == 2 || deposit == 3);
        if (isTestContract && !this.caller.equals(AION)) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        boolean isDirectDeposit = (deposit == 1 || deposit == 3);

        // Grab the requested number of periods for this contract and verify range. Since 2 byte
        // representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int periods = input[2];
        periods <<= Byte.SIZE;
        periods |= (input[3] & 0xFF);
        if (periods < 1 || periods > 1200) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Grab the precision and percentage and perform the shiftings and verify the range.
        // 2 byte representation cast to 4-byte int, result is interpreted as unsigned & positive.
        int precision = input[input.length - 1];
        if (precision < 0 || precision > 18) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // Keep first byte of percentBytes unused (as zero) so result is always unsigned.
        byte[] percentBytes = new byte[PERCENT_LEN + 1];
        System.arraycopy(input, 2 + PERIODS_LEN, percentBytes, 1,  PERCENT_LEN);
        BigInteger percent = new BigInteger(percentBytes);
        BigDecimal realPercent = new BigDecimal(percent).movePointLeft(precision);
        if (realPercent.compareTo(BigDecimal.ZERO) < 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        } else if (realPercent.compareTo(new BigDecimal("100")) > 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        // All checks OK. Generate contract address, save the new contract & return to caller.
        byte[] ownerNonce = track.getNonce(this.caller).toByteArray();
        byte[] hashInfo = new byte[ownerNonce.length + Address.ADDRESS_LEN];
        System.arraycopy(ownerNonce, 0, hashInfo, 0, ownerNonce.length);
        System.arraycopy(this.caller.toBytes(), 0, hashInfo, ownerNonce.length, Address.ADDRESS_LEN);
        byte[] trsAddr = HashUtil.h256(hashInfo);
        trsAddr[0] = TRS_PREFIX;
        Address contract = new Address(trsAddr);

        saveNewContract(contract, isTestContract, isDirectDeposit, periods, percent, precision);
        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST, contract.toBytes());
    }

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

        byte[] specKey = new byte[DataWord.BYTES];
        specKey[0] = (byte) 0xC0;

        byte[] specValue = new byte[DataWord.BYTES];
        byte[] percentBytes = percent.toByteArray();
        System.arraycopy(percentBytes, 0, specValue,
            PERCENT_LEN - percentBytes.length, percentBytes.length);
        specValue[PERCENT_LEN] = (isTest) ? (byte) 0x1 : (byte) 0x0;
        specValue[PERCENT_LEN + 1] = (isDirectDeposit) ? (byte) 0x1 : (byte) 0x0;
        specValue[PERCENT_LEN + 2] = (byte) precision;
        specValue[PERCENT_LEN + 3] = (byte) ((periods >> Byte.SIZE) & 0xFF);
        specValue[PERCENT_LEN + 4] = (byte) (periods & 0xFF);

        track.createAccount(contract);
        saveContractOwner(contract);
        track.addStorageRow(contract, new DataWord(specKey), new DataWord(specValue));
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
        key1[0] = (byte) 0x80;
        track.addStorageRow(contract, new DataWord(key1), new DataWord(addr1));

        byte[] key2 = new byte[DataWord.BYTES];
        key2[0] = (byte) 0x80;
        key2[DataWord.BYTES - 1] = (byte) 0x01;
        track.addStorageRow(contract, new DataWord(key2), new DataWord(addr2));
    }

}