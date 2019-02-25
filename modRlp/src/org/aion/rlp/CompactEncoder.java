package org.aion.rlp;

import static java.util.Arrays.copyOfRange;
import static org.aion.rlp.Utils.TERMINATOR;
import static org.aion.rlp.Utils.hexEncode;
import static org.aion.rlp.Utils.hexEncodeWithTerminatorByte;

/**
 * Compact encoding of hex sequence with optional terminator
 *
 * <p>The traditional compact way of encoding a hex string is to convert it into binary - that is, a
 * string like 0f1248 would become three bytes 15, 18, 72. However, this approach has one slight
 * problem: what if the length of the hex string is odd? In that case, there is no way to
 * distinguish between, say, 0f1248 and f1248.
 *
 * <p>Additionally, our application in the Merkle Patricia tree requires the additional feature that
 * a hex string can also have a special "terminator symbol" at the end (denoted by the 'T'). A
 * terminator symbol can occur only once, and only at the end.
 *
 * <p>An alternative way of thinking about this to not think of there being a terminator symbol, but
 * instead treat bit specifying the existence of the terminator symbol as a bit specifying that the
 * given node encodes a final node, where the value is an actual value, rather than the hash of yet
 * another node.
 *
 * <p>To solve both of these issues, we force the first nibble of the final byte-stream to encode
 * two flags, specifying oddness of length (ignoring the 'T' symbol) and terminator status; these
 * are placed, respectively, into the two lowest significant bits of the first nibble. In the case
 * of an even-length hex string, we must introduce a second nibble (of value zero), in addition to
 * the encoded flags added as a first nibble, to ensure the resulting hex-string is still even in
 * length and thus is representable by a whole number of bytes.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>[ 1, 2, 3, 4, 5 ] '\x11\x23\x45'
 *   <li>[ 0, 1, 2, 3, 4, 5 ] '\x00\x01\x23\x45'
 *   <li>[ 0, 15, 1, 12, 11, 8, T ] '\x20\x0f\x1c\xb8'
 *   <li>[ 15, 1, 12, 11, 8, T ] '\x3f\x1c\xb8'
 * </ul>
 */
public class CompactEncoder {

    /**
     * Pack nibbles to binary
     *
     * @param nibbles sequence. may have a terminator
     * @return hex-encoded byte array
     * @throws NullPointerException when given a null input
     */
    public static byte[] packNibbles(byte[] nibbles) {
        int terminator = 0;

        int len = nibbles.length;
        if (len > 0 && nibbles[len - 1] == TERMINATOR) {
            terminator = 1;
            --len;
        }
        int oddlen = len & 1; // 0 or 1

        int flag = (terminator << 1) + oddlen;

        byte[] output = new byte[(len >>> 1) + 1];

        if (oddlen != 0) {
            output[0] = (byte) ((flag << 4) + nibbles[0]);
        } else {
            output[0] = (byte) (flag << 4);
        }

        for (int i = oddlen, index = 1; i < len; i += 2, index++) {
            output[index] = (byte) ((nibbles[i] << 4) + nibbles[i + 1]);
        }

        return output;
    }

    /** @throws NullPointerException when given a null input */
    public static boolean hasTerminator(byte[] packedKey) {
        return ((packedKey[0] >> 4) & 2) != 0;
    }

    /**
     * Unpack a binary string to its nibbles equivalent
     *
     * @param str of binary data
     * @return array of nibbles in byte-format
     */
    public static byte[] unpackToNibbles(byte[] str) {
        byte[] base = binToNibbles(str);

        int terminator = 0;
        if (base[0] < 2) {
            terminator = 1;
        }

        if ((base[0] & 1) == 1) {
            base = copyOfRange(base, 1, base.length - terminator);
        } else {
            base = copyOfRange(base, 2, base.length - terminator);
        }
        return base;
    }

    /**
     * Transforms a binary array to hexadecimal format + terminator
     *
     * @param str byte[]
     * @return array with each individual nibble adding a terminator at the end
     */
    public static byte[] binToNibbles(byte[] str) {
        return hexEncodeWithTerminatorByte(str);
    }

    static byte[] binToNibblesNoTerminator(byte[] str) {
        return hexEncode(str);
    }
}
