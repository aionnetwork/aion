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
import org.aion.precompiled.contracts.TotalCurrencyContract;
import org.aion.precompiled.type.IPrecompiledContract;

/**
 * A factory class that produces pre-compiled contract instances.
 */
public class ContractFactory {
    private static final String TOTAL_CURRENCY = "0000000000000000000000000000000000000000000000000000000000000100";
    //TODO: move owner addr
    private static final String TOTAL_CURRENCY_OWNER = "0xa0229b51b4e4a023b9a5e68e0a047c74f947be6cb84e564bf5f752a8fec000f9";

    private ContractFactory(){}

    /**
     * Returns a new pre-compiled contract such that the address of the new contract is address.
     * Returns null if address does not map to any known contracts.
     *
     * @param address The contract address.
     * @param track The repo.
     * @return the specified pre-compiled address.
     */
    public static IPrecompiledContract getPrecompiledContract(Address address, IRepositoryCache track) {
        if (TOTAL_CURRENCY.equals(address.toString())) {
            return new TotalCurrencyContract(track, address, Address.wrap(TOTAL_CURRENCY_OWNER));
        }
        return null;
    }

    /**
     * Returns true if address is the address of a pre-compiled contract and false otherwise.
     *
     * @param address The address to check.
     * @return true iff address is address of a pre-compiled contract.
     */
    public static boolean isPrecompiledContract(Address address) {
        if (TOTAL_CURRENCY.equals(address.toString())) {
            return true;
        }
        return false;
    }

    /**
     * Returns the address of the TotalCurrencyContract contract.
     *
     * @return The contract address.
     */
    public static Address getTotalCurrencyContractAddress() {
        return Address.wrap(TOTAL_CURRENCY);
    }
}
