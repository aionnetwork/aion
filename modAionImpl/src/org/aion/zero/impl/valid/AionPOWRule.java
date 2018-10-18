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

import java.math.BigInteger;
import java.util.List;

import org.aion.base.util.ByteUtil;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.crypto.HashUtil;

import static org.aion.base.util.Hex.toHexString;

/**
 * Checks proof value against its boundary for the block header
 */
public class AionPOWRule extends BlockHeaderRule<A0BlockHeader> {

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        BigInteger boundary = header.getPowBoundaryBI();

        byte[] hdrBytes = header.getMineHash();
        byte[] input = new byte[32 + 32 + 1408]; // H(Hdr) + nonce + solution

        int pos = 0;
        System.arraycopy(hdrBytes, 0, input, pos, hdrBytes.length);
        System.arraycopy(header.getNonce(), 0, input, pos+=32, 32);
        System.arraycopy(header.getSolution(), 0, input, pos+=32, 1408);

        BigInteger hash = new BigInteger(1, HashUtil.h256(input));

        if (hash.compareTo(boundary) >= 0) {
            addError(formatError(hash, boundary), errors);
            return false;
        }
        return true;
    }

    private static String formatError(BigInteger actual, BigInteger boundary) {
        return "computed output ("
                + actual
                + ") violates boundary condition ("
                + boundary + ")";
    }
}
