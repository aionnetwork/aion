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

package org.aion.equihash;

public class StepRow {

    private int width;
    private byte[] hash;
    private int hInLen;
    private int hLen;
    private int cBitLength;

    public StepRow(int width, byte[] hashIn, int hInLen, int hLen, int cBitLength) throws NullPointerException {
        if (hashIn == null)
            throw new NullPointerException("Null hashIn");

        this.width = width;
        this.hInLen = hInLen;
        this.hLen = hLen;
        this.cBitLength = cBitLength;
        this.hash = new byte[width];

        // Byte pad is 0 based on the equihash specification
        EquiUtils.extendArray(hashIn, hInLen, this.hash, hLen, cBitLength, 0);
    }

    public StepRow(int width, StepRow a) {
        this.hash = new byte[width];
        System.arraycopy(a.getHash(), 0, this.getHash(), 0, a.width);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public int gethInLen() {
        return hInLen;
    }

    public void sethInLen(int hInLen) {
        this.hInLen = hInLen;
    }

    public int gethLen() {
        return hLen;
    }

    public void sethLen(int hLen) {
        this.hLen = hLen;
    }

    public int getcBitLength() {
        return cBitLength;
    }

    public void setcBitLength(int cBitLength) {
        this.cBitLength = cBitLength;
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
