package org.aion.base.util;

import java.util.Arrays;

public final class FastByteComparisons {
    /**
     * Check if two byte arrays are equal.
     *
     * @param array1
     * @param array2
     * @return
     */
    public static boolean equal(byte[] array1, byte[] array2) {
        return Arrays.equals(array1, array2);
    }

    /**
     * Compares two byte arrays.
     *
     * @param array1
     * @param array2
     * @return
     */
    public static int compareTo(byte[] array1, byte[] array2) {
        return Arrays.compare(array1, array2);
    }

    /**
     * Compares two regions of byte array.
     *
     * @param array1
     * @param offset1
     * @param size1
     * @param array2
     * @param offset2
     * @param size2
     * @return
     */
    public static int compareTo(
            byte[] array1, int offset1, int size1, byte[] array2, int offset2, int size2) {
        byte[] b1 = Arrays.copyOfRange(array1, offset1, offset1 + size1);
        byte[] b2 = Arrays.copyOfRange(array2, offset2, offset2 + size2);

        return Arrays.compare(b1, b2);
    }
}
