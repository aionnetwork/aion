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
package org.aion.equihash;

import static org.aion.base.util.ByteUtil.intToBytes;

class FullStepRow extends StepRow {

    public FullStepRow(int width, byte[] hashIn, int hInLen, int hLen, int cBitLen, int index)
        throws NullPointerException {
        super(width, hashIn, hInLen, cBitLen);

        byte[] indexBytes = intToBytes(index);
        System.arraycopy(indexBytes, 0, this.getHash(), hLen, indexBytes.length);
    }

    public FullStepRow(int width, FullStepRow a, FullStepRow b, int len, int lenIndices, int trim)
        throws NullPointerException {
        super(width, a);

        // Value of a is checked in super()
        if (b == null) {
            throw new NullPointerException("null FullStepRow");
        }

        // Merge a and b
        for (int i = trim; i < len; i++) {
            this.getHash()[i - trim] = (byte) (a.getHash()[i] ^ b.getHash()[i]);
        }

        if (EquiUtils.indicesBefore(a, b, len, lenIndices)) {
            System.arraycopy(a.getHash(), len, this.getHash(), len - trim, lenIndices);
            System.arraycopy(b.getHash(), len, this.getHash(), len - trim + lenIndices, lenIndices);
        } else {
            System.arraycopy(b.getHash(), len, this.getHash(), len - trim, lenIndices);
            System.arraycopy(a.getHash(), len, this.getHash(), len - trim + lenIndices, lenIndices);
        }
    }
}
