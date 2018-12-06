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
package org.aion.gui.model;

import java.math.BigInteger;
import org.aion.api.type.ApiMsg;
import org.aion.base.type.AionAddress;

public class BalanceRetriever extends AbstractAionApiClient {

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public BalanceRetriever(KernelConnection kernelConnection) {
        super(kernelConnection);
    }

    /**
     * Get balance of given account
     *
     * @param address address of account
     * @return if API is connected, balance of that account; otherwise, null
     */
    public BigInteger getBalance(String address) {
        // TODO Prefer Optional over null.
        final BigInteger balance;
        if (!apiIsConnected()) {
            balance = null;
        } else {
            ApiMsg msg = callApi(api -> api.getChain().getBalance(new AionAddress(address)));
            balance = msg.getObject();
        }
        return balance;
    }
}
