package org.aion.precompiled;

import javax.annotation.Nonnull;

public class PrecompiledUtilities {
    public static byte[] pad(@Nonnull final byte[] input,
                      final int length) {
        assert input.length <= length;
        if (input.length == length)
            return input;

        byte[] out = new byte[length];
        System.arraycopy(input, 0, out, out.length - input.length, input.length);
        return out;
    }
}
