package org.aion.rlp;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

class Utils {

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
}
