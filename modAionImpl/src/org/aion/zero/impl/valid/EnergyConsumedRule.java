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
package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/**
 * Rule for checking that energyConsumed does not exceed energyLimit:
 * assert(blockHeader.energyConsumed <= blockHeader.energyLimit)
 */
public class EnergyConsumedRule extends BlockHeaderRule<A0BlockHeader> {

    private static String formatError(long energyConsumed, long energyLimit) {
        return "energyConsumed ("
            + energyConsumed
            + ") > energyLimit("
            + energyLimit + ")";
    }

    @Override
    public boolean validate(A0BlockHeader blockHeader, List<RuleError> error) {
        if (blockHeader.getEnergyConsumed() > blockHeader.getEnergyLimit()) {
            addError(formatError(blockHeader.getEnergyConsumed(), blockHeader.getEnergyLimit()),
                error);
            return false;
        }
        return true;
    }
}
