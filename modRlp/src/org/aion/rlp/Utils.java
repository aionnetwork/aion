package org.aion.rlp;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

class Utils {

    static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    static final byte[] encodingTable = {
        (byte) '0',
        (byte) '1',
        (byte) '2',
        (byte) '3',
        (byte) '4',
        (byte) '5',
        (byte) '6',
        (byte) '7',
        (byte) '8',
        (byte) '9',
        (byte) 'a',
        (byte) 'b',
        (byte) 'c',
        (byte) 'd',
        (byte) 'e',
        (byte) 'f'
    };

    private static final Map<Character, Byte> hexMap = new HashMap<>();

    static {
        hexMap.put('0', (byte) 0x0);
        hexMap.put('1', (byte) 0x1);
        hexMap.put('2', (byte) 0x2);
        hexMap.put('3', (byte) 0x3);
        hexMap.put('4', (byte) 0x4);
        hexMap.put('5', (byte) 0x5);
        hexMap.put('6', (byte) 0x6);
        hexMap.put('7', (byte) 0x7);
        hexMap.put('8', (byte) 0x8);
        hexMap.put('9', (byte) 0x9);
        hexMap.put('a', (byte) 0xa);
        hexMap.put('b', (byte) 0xb);
        hexMap.put('c', (byte) 0xc);
        hexMap.put('d', (byte) 0xd);
        hexMap.put('e', (byte) 0xe);
        hexMap.put('f', (byte) 0xf);
    }

    static final byte TERMINATOR = 16;

    static byte[] concatenate(byte[] a, byte[] b) {
        byte[] ret = new byte[a.length + b.length];
        System.arraycopy(a, 0, ret, 0, a.length);
        System.arraycopy(b, 0, ret, a.length, b.length);
        return ret;
    }

    static byte[] asUnsignedByteArray(BigInteger bi) {
        byte[] bytes = bi.toByteArray();

        if (bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];

            System.arraycopy(bytes, 1, tmp, 0, tmp.length);

            return tmp;
        }

        return bytes;
    }

    static byte[] hexEncodeWithTerminatorByte(byte[] in) {
        if (in == null) {
            return null;
        }
        byte[] ret = new byte[(in.length << 1) + 1];

        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xff;
            ret[i << 1] = hexMap.get((char) encodingTable[v >>> 4]);
            ret[(i << 1) + 1] = hexMap.get((char) encodingTable[v & 0xf]);
        }

        ret[ret.length - 1] = TERMINATOR;

        return ret;
    }

    static byte[] hexEncode(byte[] in) {
        if (in == null) {
            return null;
        }
        byte[] ret = new byte[in.length << 1];

        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xff;
            ret[i << 1] = hexMap.get((char) encodingTable[v >>> 4]);
            ret[(i << 1) + 1] = hexMap.get((char) encodingTable[v & 0xf]);
        }

        return ret;
    }

    static String oneByteToHexString(byte value) {
        String retVal = Integer.toString(value & 0xFF, 16);
        if (retVal.length() == 1) {
            retVal = "0" + retVal;
        }
        return retVal;
    }

    static boolean isSingleZero(byte[] array) {
        return (array.length == 1 && array[0] == 0);
    }

    static boolean isNullOrZeroArray(byte[] array) {
        return (array == null) || (array.length == 0);
    }

    public static int byteArrayToInt(byte[] b) {
        if (b == null || b.length == 0) {
            return 0;
        }
        return new BigInteger(1, b).intValue();
    }

    /**
     * Cast hex encoded value from byte[] to long
     *
     * <p>Limited to Long.MAX_VALUE: 2^63-1 (8 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive long value.
     */
    public static long byteArrayToLong(byte[] b) {
        if (b == null || b.length == 0) {
            return 0;
        }
        return new BigInteger(1, b).longValue();
    }

    /**
     * Converts a int value into a byte array.
     *
     * @param val - int value to convert
     * @return value with leading byte that are zeroes striped
     */
    static byte[] intToBytesNoLeadZeroes(int val) {
        if (val == 0) {
            return EMPTY_BYTE_ARRAY;
        } else if (val < 0 || val > 16777215) {
            return new byte[] {
                (byte) ((val >>> 24) & 0xFF),
                (byte) ((val >>> 16) & 0xFF),
                (byte) ((val >>> 8) & 0xFF),
                (byte) (val & 0xFF)
            };
        } else if (val < 256) {
            return new byte[] {(byte) (val & 0xFF)};
        } else if (val < 65536) {
            return new byte[] {(byte) ((val >>> 8) & 0xFF), (byte) (val & 0xFF)};
        } else {
            return new byte[] {
                (byte) ((val >>> 16) & 0xFF), (byte) ((val >>> 8) & 0xFF), (byte) (val & 0xFF)
            };
        }
    }

    /**
     * Turn nibbles to a pretty looking output string
     *
     * <p>Example. [ 1, 2, 3, 4, 5 ] becomes '\x11\x23\x45'
     *
     * @param nibbles - getting byte of data [ 04 ] and turning it to a '\x04' representation
     * @return pretty string of nibbles
     */
    static String nibblesToPrettyString(byte[] nibbles) {
        StringBuilder builder = new StringBuilder();
        for (byte nibble : nibbles) {
            final String nibbleString = oneByteToHexString(nibble);
            builder.append("\\x").append(nibbleString);
        }
        return builder.toString();
    }
}
