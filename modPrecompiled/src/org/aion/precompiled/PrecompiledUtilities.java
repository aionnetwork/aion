package org.aion.precompiled;

import javax.annotation.Nonnull;

public class PrecompiledUtilities {

    /**
     * Returns input as a byte array of length length, padding with zero bytes as needed to achieve
     * the desired length. Returns null if input.length is larger than the specified length to pad
     * to.
     *
     * @param input The input array to pad.
     * @param length The length of the newly padded array.
     * @return input zero-padded to desired length or null if input.length > length.
     */
    public static byte[] pad(@Nonnull final byte[] input, final int length) {
        if (input.length > length) {
            return null;
        }

        if (input.length == length) {
            return input;
        }

        byte[] out = new byte[length];
        System.arraycopy(input, 0, out, out.length - input.length, input.length);
        return out;
    }
}
