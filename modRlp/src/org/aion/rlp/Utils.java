package org.aion.rlp;

import java.math.BigInteger;

public class Utils {

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

    static byte[] hexEncode(byte[] in) {
        return hexEncode(in, false);
    }

    static byte[] hexEncode(byte[] in, boolean withTerminatorByte) {
        if (in == null) {
            return null;
        }
        byte[] ret = new byte[in.length * 2 + (withTerminatorByte ? 1 : 0)];

        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xff;
            ret[i * 2] = encodingTable[v >>> 4];
            ret[i * 2 + 1] = encodingTable[v & 0xf];
        }

        return ret;
    }
}
