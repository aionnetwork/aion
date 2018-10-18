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
package org.aion.mcf.valid;

import java.util.List;
import org.aion.base.type.IBlockHeader;

public class BlockNumberRule<BH extends IBlockHeader> extends DependentBlockHeaderRule<BH> {

    private static String formatError(long headerNumber, long parentNumber) {
        return "blockNumber ("
            + headerNumber
            + ") is not equal to parentBlock number + 1 ("
            + parentNumber + ")";
    }

    @Override
    public boolean validate(BH header, BH parent, List<RuleError> errors) {
        if (header.getNumber() != (parent.getNumber() + 1)) {
            addError(formatError(header.getNumber(), parent.getNumber()), errors);
            return false;
        }
        return true;
    }
}
