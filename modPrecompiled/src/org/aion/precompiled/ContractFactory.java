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
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.contracts.ATB.TokenBridgeContract;
import org.aion.vm.ExecutionContext;
import org.aion.vm.IPrecompiledContract;
import org.aion.precompiled.contracts.TotalCurrencyContract;

/**
 * A factory class that produces pre-compiled contract instances.
 */
public class ContractFactory {
    private static final String OWNER = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String TOTAL_CURRENCY = "0000000000000000000000000000000000000000000000000000000000000100";

    private static final String TOKEN_BRIDGE = "0000000000000000000000000000000000000000000000000000000000000200";
    private static final String TOKEN_BRIDGE_INITIAL_OWNER = "0000000000000000000000000000000000000000000000000000000000000000";

    public ContractFactory(){}

    /**
     * Returns a new pre-compiled contract such that the getRecipient of the new contract is getRecipient.
     * Returns null if getRecipient does not map to any known contracts.
     *
     * @param context Passed in execution context.
     * @param track The repo.
     * @return the specified pre-compiled getRecipient.
     */
    public static IPrecompiledContract getPrecompiledContract(ExecutionContext context,
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase <?, ?>> track) {

        switch (context.getRecipient().toString()) {
            case TOTAL_CURRENCY:
                return new TotalCurrencyContract(track, context.getCaller(), Address.wrap(OWNER));
            case TOKEN_BRIDGE:
                TokenBridgeContract contract = new TokenBridgeContract(context,
                        track, Address.wrap(TOKEN_BRIDGE_INITIAL_OWNER), Address.wrap(TOKEN_BRIDGE));
                return contract;
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
     * Returns true if getRecipient is the getRecipient of a pre-compiled contract and false otherwise.
     *
     * @param address The getRecipient to check.
     * @return true iff getRecipient is getRecipient of a pre-compiled contract.
     */
    public static boolean isPrecompiledContract(Address address) {
        switch (address.toString()) {
            case TOTAL_CURRENCY: return true;
            case TOKEN_BRIDGE: return true;
            default: return false;
        }
    }

    /**
     * Returns the getRecipient of the TotalCurrencyContract contract.
     *
     * @return the contract getRecipient.
     */
    public static Address getTotalCurrencyContractAddress() {
        return Address.wrap(TOTAL_CURRENCY);
    }

}
