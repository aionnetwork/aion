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

import java.util.List;

public class AionExtraDataRule extends BlockHeaderRule<A0BlockHeader> {

    private final int maximumExtraDataSize;

    public AionExtraDataRule(int maximumExtraDataSize) {
        if (maximumExtraDataSize < 0)
            throw new IllegalArgumentException("extra data size must be >= 0");

        this.maximumExtraDataSize = maximumExtraDataSize;
    }

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        if (header.getExtraData() != null && header.getExtraData().length > this.maximumExtraDataSize) {
            addError(String.format("extraData (%d) > MAXIMUM_EXTRA_DATA_SIZE (%d)", header.getExtraData().length,
                    this.maximumExtraDataSize), errors);
            return false;
        }
        return true;
    }
}
