package org.aion.precompiled.type;

import java.util.Arrays;

/**
 * A data word implementation in which the size of the data word is 32 bytes.
 *
 * This is a 'double' data word since it is double the size of {@link PrecompiledDataWord}.
 */
public final class PrecompiledDoubleDataWord implements IPrecompiledDataWord {
    public static final int SIZE = 32;
    private final byte[] data;

    private PrecompiledDoubleDataWord(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("Cannot create data word using null bytes.");
        }

        if (bytes.length == SIZE) {
            this.data = Arrays.copyOf(bytes, bytes.length);
        } else if (bytes.length < SIZE) {
            this.data = rightPadBytesWithZeroes(bytes);
        } else {
            throw new IllegalArgumentException("PrecompiledDataWord length cannot exceed 16 bytes!");
        }
    }

    /**
     * Returns a new data word that wraps the given bytes. If the length of the given bytes is less
     * than 32 they will be right-padded with zero bytes.
     *
     * @param bytes The bytes to wrap.
     * @return the data word.
     */
    public static PrecompiledDoubleDataWord fromBytes(byte[] bytes) {
        return new PrecompiledDoubleDataWord(bytes);
    }

    /**
     * Returns a copy of the underlying bytes.
     *
     * @return the underlying bytes.
     */
    @Override
    public byte[] copyOfData() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    private static byte[] rightPadBytesWithZeroes(byte[] bytes) {
        byte[] paddedBytes = new byte[SIZE];
        System.arraycopy(bytes, 0, paddedBytes, SIZE - bytes.length, bytes.length);
        return paddedBytes;
    }
}
