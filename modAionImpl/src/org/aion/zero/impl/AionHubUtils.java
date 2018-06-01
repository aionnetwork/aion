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
package org.aion.zero.impl;

import java.math.BigInteger;
import java.util.Map;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.PrecompiledContracts;
import org.aion.zero.impl.db.AionRepositoryImpl;

/** {@link AionHub} functionality where a full instantiation of the class is not desirable. */
public class AionHubUtils {

    public static void buildGenesis(AionGenesis genesis, AionRepositoryImpl repository) {
        // initialization section for network balance contract
        IRepositoryCache track = repository.startTracking();

        Address networkBalanceAddress = PrecompiledContracts.totalCurrencyAddress;
        track.createAccount(networkBalanceAddress);

        for (Map.Entry<Integer, BigInteger> addr : genesis.getNetworkBalances().entrySet()) {
            track.addStorageRow(
                    networkBalanceAddress,
                    new DataWord(addr.getKey()),
                    new DataWord(addr.getValue()));
        }

        for (Address addr : genesis.getPremine().keySet()) {
            track.createAccount(addr);
            track.addBalance(addr, genesis.getPremine().get(addr).getBalance());
        }
        track.flush();

        repository.commitBlock(genesis.getHeader());
        repository.getBlockStore().saveBlock(genesis, genesis.getDifficultyBI(), true);
    }
}
