package org.aion.precompiled.type;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A data word implementation in which the size of the data word is 16 bytes.
 *
 * This class is immutable.
 */
public final class PrecompiledDataWord implements IPrecompiledDataWord {
    public static final int SIZE = 16;
    private final byte[] data;

    private PrecompiledDataWord(byte[] bytes) {
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
     * than 16 they will be right-padded with zero bytes.
     *
     * @param bytes The bytes to wrap.
     * @return the data word.
     */
    public static PrecompiledDataWord fromBytes(byte[] bytes) {
        return new PrecompiledDataWord(bytes);
    }

    /**
     * Returns a new data word whose underlying byte array consists of 12 zero bytes (in the
     * left-most bytes) followed by the 4 bytes of the given integer.
     *
     * @param integer The integer.
     * @return the data word.
     */
    public static PrecompiledDataWord fromInt(int integer) {
        return new PrecompiledDataWord(ByteBuffer.allocate(SIZE).position(12).putInt(integer).array());
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
