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

import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.crypto.HashUtil;

/**
 * Checks proof value against its boundary for the block header
 */
public class AionPOWRule extends BlockHeaderRule<A0BlockHeader> {

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        BigInteger boundary = header.getPowBoundaryBI();

        // 32 byte static hash, 8 byte dynamic, 32 byte nonce, 1408 solution (1480 input)
        byte[] validationBytes = new byte[1480];
        byte[] staticHash = header.getStaticHash();
        byte[] dynamic = BigInteger.valueOf(header.getTimestamp()).toByteArray();
        byte[] nonce = header.getNonce();
        byte[] solution = header.getSolution();
        int pos = 0;

        System.arraycopy(header.getStaticHash(), 0, validationBytes, pos, staticHash.length);
        pos += staticHash.length;

        System.arraycopy(dynamic, 0, validationBytes, pos, dynamic.length);
        pos += dynamic.length;

        System.arraycopy(nonce, 0, validationBytes, pos, nonce.length);
        pos += nonce.length;

        System.arraycopy(nonce, 0, validationBytes, pos, solution.length);

        BigInteger hash = new BigInteger(1, validationBytes);

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
