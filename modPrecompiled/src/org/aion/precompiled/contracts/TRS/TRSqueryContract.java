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

import java.util.Arrays;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;

/**
 * The TRSqueryContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since the
 * TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * The TRSqueryContract is the component of the public-facing TRS contract that users of the contract
 * (as well as the owner) interact with in order to make simple queries on the contract. None of the
 * operations supported here will change the state of the contract. This contract extends
 * StatefulPrecompiledContract not because it changes state but only for database access.
 *
 * The following operations are supported:
 *      isStarted -- checks whether a TRS contract is live.
 *      isLocked -- checks whether a TRS contract is locked.
 *      isDirectDeposit -- checks whether a depositor can directly deposit to a TRS contract or not.
 *      period -- checks the current period that a TRS contract is in.
 *      periodAt -- checks the period that a TRS contract is in at a specific block.
 *      availableForWithdrawalAt -- checks the fraction of total withdrawable funds for a contract.
 */
public final class TRSqueryContract extends AbstractTRS {

    /**
     * Constructs a new TRSqueryContract that will use repo as the database cache to update its
     * state with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public TRSqueryContract(
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
     *   <b>operation 0x0</b> - returns true iff the specified public-facing TRS contract is live.
     *     [<32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract to query.
     *
     *     conditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
     *
     *                                           ~~~***~~~
     *
     *   <b>operation 0x1</b> - returns true iff the specified public-facing TRS contract is locked.
     *     If a contract is live then it must also be locked and so a live contract will return true.
     *     [<32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract to query.
     *
     *     conditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
     *
     *                                           ~~~***~~~
     *
     *   <b>operation 0x2</b> - returns true iff the specified public-facing TRS contract has direct
     *     deposits enabled.
     *     [<32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract to query.
     *
     *     coditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
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
            case 0: return isStarted(input, nrgLimit);
            case 1: return isLocked(input, nrgLimit);
            case 2: return isDirectDepositEnabled(input, nrgLimit);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not it is live.
     *
     * The input byte array format is defined as follows:
     *     [<1b - 0x0> | <32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract.
     *
     *     conditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for liveness.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult isStarted(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] result = new byte[1];
        Address contract = Address.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (isContractLive(getContractSpecs(contract))) {
            result[0] = 0x1;
        }
        return new ContractExecutionResult(ResultCode.SUCCESS, COST - nrgLimit, result);
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not it is locked.
     *
     * The input byte array format is defined as follows:
     *     [<1b - 0x0> | <32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract.
     *
     *     conditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for lockedness.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult isLocked(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] result = new byte[1];
        Address contract = Address.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (isContractLocked(getContractSpecs(contract))) {
            result[0] = 0x1;
        }
        return new ContractExecutionResult(ResultCode.SUCCESS, COST - nrgLimit, result);
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not direct deposits are
     * enabled for it.
     *
     * The input byte array format is defined as follows:
     *     [<1b - 0x2> | <32b - contractAddress>]
     *     total = 33 bytes
     *   where:
     *     contractAddress is the address of the public-facing TRS contract.
     *
     *     conditions: none.
     *
     *     returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     *       0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for direct deposits.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private ContractExecutionResult isDirectDepositEnabled(byte[] input, long nrgLimit) {
        // Some "constants"
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        byte[] result = new byte[1];
        Address contract = Address.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (isDirDepositsEnabled(getContractSpecs(contract))) {
            result[0] = 0x1;
        }
        return new ContractExecutionResult(ResultCode.SUCCESS, COST - nrgLimit, result);
    }

}