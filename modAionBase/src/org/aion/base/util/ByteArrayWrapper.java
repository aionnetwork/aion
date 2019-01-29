package org.aion.base.util;

import java.io.Serializable;
import java.util.Arrays;

public class ByteArrayWrapper
        implements Comparable<ByteArrayWrapper>, Serializable, Bytesable<ByteArrayWrapper> {

    private static final long serialVersionUID = -2937011296133778157L;
    private final byte[] data;
    private int hashCode = 0;

    public ByteArrayWrapper(byte[] data) {
        if (data == null) {
            throw new NullPointerException("Data must not be null");
        }
        this.data = data;
        this.hashCode = Arrays.hashCode(data);
    }

    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper)) {
            return false;
        }

        return Arrays.equals(data, ((ByteArrayWrapper) other).getData());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ByteArrayWrapper o) {
        return Arrays.compare(data, o.getData());
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return Hex.toHexString(data);
    }

    // toBytes() and getData() have identical functionality
    @Override
    public byte[] toBytes() {
        return data;
    }

    @Override
    public ByteArrayWrapper fromBytes(byte[] bs) {
        return new ByteArrayWrapper(bs);
    }

    public static ByteArrayWrapper wrap(byte[] data) {
        return new ByteArrayWrapper(data);
    }

    /**
     * Checks if every byte in the array has the value 0.
     *
     * @return {@code true} if every byte in the array has the value 0, {@code false} otherwise
     */
    public boolean isZero() {
        int length = data.length;
        for (int i = 0; i < length; i++) {
            if (data[length - 1 - i] != 0) {
                return false;
            }
        }
        return true;
    }

    public ByteArrayWrapper copy() {
        int length = data.length;
        byte[] bs = new byte[length];
        System.arraycopy(data, 0, bs, 0, length);
        return new ByteArrayWrapper(bs);
    }

    public static final ByteArrayWrapper ZERO = ByteArrayWrapper.wrap(new byte[] {0});

    public byte[] getNoLeadZeroesData() {
        return ByteUtil.stripLeadingZeroes(data);
    }
}
