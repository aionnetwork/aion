package org.aion.precompiled.util;

import java.util.Arrays;

public class ByteUtil {

    public static final byte[] EMPTY_WORD = new byte[32];
    public static final byte[] EMPTY_HALFWORD = new byte[16];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final byte[] ZERO_BYTE_ARRAY = new byte[] {0};

    /**
     * Convert a byte-array into a hex String.<br>
     * Works similar to {@link Hex#toHexString} but allows for <code>null</code>
     *
     * @param data - byte-array to convert to a hex-string
     * @return hex representation of the data.<br>
     *     Returns an empty String if the input is <code>null</code> TODO: swap out with more
     *     efficient implementation, for now seems like we are stuck with this
     * @see Hex#toHexString
     */
    public static String toHexString(byte[] data) {
        return data == null ? "" : Hex.toHexString(data);
    }

    /**
     * increment byte array as a number until max is reached
     *
     * @param bytes byte[]
     * @return boolean
     */
    public static boolean increment(byte[] bytes) {
        final int startIndex = 0;
        int i;
        for (i = bytes.length - 1; i >= startIndex; i--) {
            bytes[i]++;
            if (bytes[i] != 0) {
                break;
            }
        }
        // we return false when all bytes are 0 again
        return (i >= startIndex || bytes[startIndex] != 0);
    }

    public static byte[] and(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        }
        byte[] ret = new byte[b1.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) (b1[i] & b2[i]);
        }
        return ret;
    }

    public static byte[] or(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        }
        byte[] ret = new byte[b1.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) (b1[i] | b2[i]);
        }
        return ret;
    }

    /**
     * @param arrays - arrays to merge
     * @return - merged array
     */
    public static byte[] merge(byte[]... arrays) {
        int count = 0;
        for (byte[] array : arrays) {
            count += array.length;
        }

        // Create new array and copy all array contents
        byte[] mergedArray = new byte[count];
        int start = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, start, array.length);
            start += array.length;
        }
        return mergedArray;
    }

    public static int length(byte[]... bytes) {
        int result = 0;
        for (byte[] array : bytes) {
            result += (array == null) ? 0 : array.length;
        }
        return result;
    }

    /**
     * Converts string hex representation to data bytes Accepts following hex: - with or without 0x
     * prefix - with no leading 0, like 0xabc v.s. 0x0abc
     *
     * @param data String like '0xa5e..' or just 'a5e..'
     * @return decoded bytes array
     */
    public static byte[] hexStringToBytes(String data) {
        if (data == null) {
            return EMPTY_BYTE_ARRAY;
        }
        if (data.startsWith("0x")) {
            data = data.substring(2);
        }
        if ((data.length() & 1) == 1) {
            data = "0" + data;
        }
        return Hex.decode(data);
    }

    /**
     * Chops a 32-byte value into a 16-byte value. Keep in mind the subtlety that a "chopped"
     * bytearray is a different reference from a "unchopped" bytearray, so make no assummptions as
     * to whether this function news the element.
     *
     * @return 16-byte value representing the LOWER portion of the original
     */
    public static byte[] chop(byte[] in) {
        if (in.length <= 16) {
            return in;
        }
        return Arrays.copyOfRange(in, in.length - 16, in.length);
    }
}
