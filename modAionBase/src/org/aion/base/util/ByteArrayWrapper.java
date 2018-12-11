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
        byte[] otherData = ((ByteArrayWrapper) other).getData();
        return FastByteComparisons.compareTo(data, 0, data.length, otherData, 0, otherData.length)
                == 0;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ByteArrayWrapper o) {
        return FastByteComparisons.compareTo(
                data, 0, data.length, o.getData(), 0, o.getData().length);
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
}
