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

class StepRow {

    private int width;
    private byte[] hash;

    /**
     * @throws NullPointerException when given null input
     */
    StepRow(int width, byte[] hashIn, int hInLen, int cBitLength) {
        if (hashIn == null) {
            throw new NullPointerException("Null hashIn");
        }

        this.width = width;
        this.hash = new byte[width];

        // Byte pad is 0 based on the equihash specification
        EquiUtils.extendArray(hashIn, hInLen, this.hash, cBitLength, 0);
    }

    StepRow(int width, StepRow a) {
        this.hash = new byte[width];
        System.arraycopy(a.getHash(), 0, this.getHash(), 0, a.width);
    }

    public byte[] getHash() {
        return hash;
    }

    public boolean isZero(int len) {
        for (int i = 0; i < len; i++) {
            if (this.getHash()[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
