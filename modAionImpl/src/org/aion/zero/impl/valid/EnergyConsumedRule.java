/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.zero.impl.valid;

import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/**
 * Rule for checking that energyConsumed does not exceed energyLimit:
 * assert(blockHeader.energyConsumed <= blockHeader.energyLimit)
 */
public class EnergyConsumedRule extends BlockHeaderRule<A0BlockHeader> {
    @Override
    public boolean validate(A0BlockHeader blockHeader) {
        errors.clear();

        if (blockHeader.getEnergyConsumed() > blockHeader.getEnergyLimit()) {
            errors.add("energyConsumed greater than energyLimit");
            return false;
        }
        return true;
    }
}
