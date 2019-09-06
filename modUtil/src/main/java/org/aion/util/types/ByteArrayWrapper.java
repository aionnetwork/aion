package org.aion.util.types;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable class used to wrap byte arrays for use in scenarios where value-based equality needs to
 * be applied.
 */
public final class ByteArrayWrapper implements Comparable<ByteArrayWrapper> {

    private final byte[] bytes;
    private final int hashCode;

    // utility used by toString
    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    private ByteArrayWrapper(byte[] bytes) {
        Objects.requireNonNull(bytes, "The given byte array must not be null.");
        this.bytes = bytes.clone();
        this.hashCode = Arrays.hashCode(this.bytes);
    }

    /**
     * Returns a wrapper for the give byte array.
     *
     * @param bytes non-{@code null} byte array to be wrapped
     * @return a wrapper for the give byte array
     */
    public static ByteArrayWrapper wrap(byte[] bytes) {
        return new ByteArrayWrapper(bytes);
    }

    /**
     * Returns a copy of the encapsulated byte array.
     *
     * @return a copy of the encapsulated byte array
     */
    public byte[] toBytes() {
        return bytes.clone();
    }

    /**
     * Returns the length of the wrapped byte array.
     *
     * @return the length of the wrapped byte array
     */
    public int length() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ByteArrayWrapper)) {
            return false;
        }
        ByteArrayWrapper other = (ByteArrayWrapper) o;
        return Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public int compareTo(ByteArrayWrapper o) {
        // do not use getter here to avoid performance loss due to cloning
        return Arrays.compare(this.bytes, o.bytes);
    }

    @Override
    public String toString() {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Checks if every byte in the array has the value 0.
     *
     * @return {@code true} if every byte in the array has the value 0, {@code false} otherwise
     */
    public boolean isZero() {
        int length = bytes.length;
        for (int i = 0; i < length; i++) {
            if (bytes[length - 1 - i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the stored byte array is empty.
     *
     * @return {@code true} if empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public byte[] getNoLeadZeroesData() {
        final int firstNonZero = firstNonZeroByte(bytes);
        switch (firstNonZero) {
            case -1:
                // this should not be a constant because the first byte could be modified
                return new byte[] {0};
            case 0:
                // must never share access to the mutable object
                return bytes.clone();
            default:
                byte[] result = new byte[bytes.length - firstNonZero];
                System.arraycopy(bytes, firstNonZero, result, 0, bytes.length - firstNonZero);
                return result;
        }
    }

    private static int firstNonZeroByte(byte[] bytes) {
        for (int i = 0; i < bytes.length; ++i) {
            if (bytes[i] != 0) {
                return i;
            }
        }
        return -1;
    }
}
