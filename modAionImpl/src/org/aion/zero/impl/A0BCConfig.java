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

import org.aion.vm.api.interfaces.Address;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;

public interface A0BCConfig {

    /**
     * Retrieve the currently set coinbase for this particular node, blocks mined with this node
     * will use this as the coinbase.
     *
     * @return {@code coinbase} a 32-bytes address
     */
    Address getCoinbase();

    /**
     * Retrieve the currently set extra data for this particular node, blocks mined with this node
     * will use this as extra data.
     *
     * @return {@code extraData} a (up to) 32-byte value
     */
    byte[] getExtraData();

    /**
     * Retrieves whether the kernel should exit on a block conflict.
     *
     * @return {@true} if system should exit on a block conflict
     */
    boolean getExitOnBlockConflict();

    Address getMinerCoinbase();

    /** Retrieves the number indicating how many blocks between each flush */
    int getFlushInterval();

    /** Retrieves the selected energy strategy algorithm */
    AbstractEnergyStrategyLimit getEnergyLimitStrategy();
}
