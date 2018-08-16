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

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.contracts.ATB.TokenBridgeContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;
import org.aion.precompiled.contracts.TotalCurrencyContract;

/**
 * A factory class that produces pre-compiled contract instances.
 */
public class ContractFactory {
    private static final String OWNER = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String TOTAL_CURRENCY = "0000000000000000000000000000000000000000000000000000000000000100";

    private static final String TOKEN_BRIDGE = "0000000000000000000000000000000000000000000000000000000000000200";
    private static final String TOKEN_BRIDGE_INITIAL_OWNER = "a008d7b29e8d1f4bfab428adce89dc219c4714b2c6bf3fd1131b688f9ad804aa";
    public static final String TEST_PC = "9999999999999999999999999999999999999999999999999999999999999999";

    public ContractFactory(){}

    /**
     * Returns a new pre-compiled contract such that the address of the new contract is address.
     * Returns null if address does not map to any known contracts.
     *
     * @param context Passed in execution context.
     * @param track The repo.
     * @return the specified pre-compiled address.
     */
    public static IPrecompiledContract getPrecompiledContract(ExecutionContext context,
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase <?, ?>> track) {

        switch (context.address().toString()) {
            case TOTAL_CURRENCY:
                return new TotalCurrencyContract(track, context.sender(), Address.wrap(OWNER));
            case TOKEN_BRIDGE:
                TokenBridgeContract contract = new TokenBridgeContract(context,
                        track, Address.wrap(TOKEN_BRIDGE_INITIAL_OWNER), Address.wrap(TOKEN_BRIDGE));
                return contract;
            case TEST_PC:
                return new TestPrecompiledContract();
            default: return null;
        }
    }

    /**
     * A non-static method that is functionally equivalent to calling the static method
     * getPrecompiledContract. This method is here to make mocking of this class easier.
     */
    public IPrecompiledContract fetchPrecompiledContract(ExecutionContext context,
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase <?, ?>> track) {

        return getPrecompiledContract(context, track);
    }

    /**
     * Returns true if address is the address of a pre-compiled contract and false otherwise.
     *
     * @param address The address to check.
     * @return true iff address is address of a pre-compiled contract.
     */
    public static boolean isPrecompiledContract(Address address) {
        switch (address.toString()) {
            case TOTAL_CURRENCY: return true;
            case TOKEN_BRIDGE: return true;
            default: return false;
        }
    }

    /**
     * Returns the address of the TotalCurrencyContract contract.
     *
     * @return the contract address.
     */
    public static Address getTotalCurrencyContractAddress() {
        return Address.wrap(TOTAL_CURRENCY);
    }

    /**
     * A mocked up precompiled contract to test with.
     */
    public static class TestPrecompiledContract implements IPrecompiledContract {
        public static final String head = "echo: ";
        public static boolean youCalledMe = false;

        /**
         * Returns a byte array that begins with the byte version of the characters in the public
         * variable 'head' followed by the bytes in input.
         */
        @Override
        public IExecutionResult execute(byte[] input, long nrgLimit) {
            youCalledMe = true;
            byte[] msg = new byte[head.getBytes().length + input.length];
            System.arraycopy(head.getBytes(), 0, msg, 0, head.getBytes().length);
            System.arraycopy(input, 0, msg, head.getBytes().length, input.length);
            return new ExecutionResult(ResultCode.SUCCESS, nrgLimit, msg);
        }

    }

}
