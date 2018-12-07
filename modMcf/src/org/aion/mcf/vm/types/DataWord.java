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
package org.aion.mcf.vm.types;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.base.vm.IDataWord;
import org.aion.vm.api.interfaces.DataWordStub;

/**
 * Data word is the basic unit data used by virtual machine. The size of a data word is 128 bits.
 */
public class DataWord implements Comparable<DataWord>, IDataWord, DataWordStub {

    public static final BigInteger MAX_VALUE =
            BigInteger.valueOf(2).pow(128).subtract(BigInteger.ONE);

    public static final DataWord ZERO = new DataWord(0);
    public static final DataWord ONE = new DataWord(1);
    public static final int BYTES = 16;

    private byte[] data;

    public DataWord() {
        data = new byte[BYTES];
    }

    public DataWord(int num) {
        ByteBuffer bb = ByteBuffer.allocate(BYTES);
        bb.position(12);
        bb.putInt(num);
        data = bb.array();
    }

    public DataWord(long num) {
        ByteBuffer bb = ByteBuffer.allocate(BYTES);
        bb.position(8);
        bb.putLong(num);
        data = bb.array();
    }

    public DataWord(byte[] data) {
        if (data == null) {
            throw new NullPointerException("Input data");
        } else if (data.length == BYTES) {
            this.data = Arrays.copyOf(data, data.length);
        } else if (data.length < BYTES) {
            this.data = new byte[BYTES];
            System.arraycopy(data, 0, this.data, BYTES - data.length, data.length);
        } else {
            throw new RuntimeException("Data word can't exceed 16 bytes: " + Hex.toHexString(data));
        }
    }

    public DataWord(BigInteger num) {
        this(num.toByteArray());
    }

    public DataWord(String data) {
        this(Hex.decode(data));
    }

    public DataWord(ByteArrayWrapper wrapper) {
        this(wrapper.getData());
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public byte[] getNoLeadZeroesData() {
        return ByteUtil.stripLeadingZeroes(data);
    }

    public BigInteger value() {
        return new BigInteger(1, data);
    }

    public int intValue() {
        int v = 0;
        for (int i = 12; i < BYTES; i++) {
            v = (v << 8) + (data[i] & 0xff);
        }

        return v;
    }

    public long longValue() {
        long v = 0;
        for (int i = 8; i < BYTES; i++) {
            v = (v << 8) + (data[i] & 0xff);
        }

        return v;
    }

    @Override
    public boolean isZero() {
        for (int i = 0; i < BYTES; i++) {
            if (data[BYTES - 1 - i] != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isNegative() {
        int result = data[0] & 0x80;
        return result == 0x80;
    }

    @Override
    public DataWord copy() {
        byte[] bs = new byte[BYTES];
        System.arraycopy(data, 0, bs, 0, BYTES);
        return new DataWord(bs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataWord dataWord = (DataWord) o;

        return Arrays.equals(data, dataWord.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public int compareTo(DataWord o) {
        return Arrays.compare(this.data, o.data);
    }

    @Override
    public String toString() {
        return Hex.toHexString(data);
    }
}
