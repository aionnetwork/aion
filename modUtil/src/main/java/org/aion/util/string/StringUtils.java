package org.aion.util.string;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.util.conversions.Hex;

public class StringUtils {

    /** Validate a passed hex string is a valid address */
    public static boolean isValidAddress(String address) {
        if (address == null || address.isEmpty() || address.length() < 64) {
            return false;
        }

        if (address.startsWith("0x")) {
            address = address.substring(2);
        }

        // Will need to change this for a1, a2....
        if (address.startsWith("a0")) {
            return address.length() == 64 && address.substring(2).matches("^[0-9A-Fa-f]+$");
        } else {
            return false;
        }
    }

    public static String getNodeIdShort(String nodeId) {
        return nodeId == null ? "<null>" : nodeId.substring(0, 8);
    }

    public static String align(String s, char fillChar, int targetLen, boolean alignRight) {
        if (targetLen <= s.length()) {
            return s;
        }
        String alignString = repeat("" + fillChar, targetLen - s.length());
        return alignRight ? alignString + s : s + alignString;
    }

    public static String repeat(String s, int n) {
        if (s.length() == 1) {
            byte[] bb = new byte[n];
            Arrays.fill(bb, s.getBytes()[0]);
            return new String(bb);
        } else {
            StringBuilder ret = new StringBuilder();
            for (int i = 0; i < n; i++) {
                ret.append(s);
            }
            return ret.toString();
        }
    }

    public static BigInteger StringNumberAsBigInt(String input) {
        if (input.startsWith("0x")) {
            return StringHexToBigInteger(input);
        } else {
            return StringDecimalToBigInteger(input);
        }
    }

    public static BigInteger StringHexToBigInteger(String input) {
        String hexa = input.startsWith("0x") ? input.substring(2) : input;
        return new BigInteger(hexa, 16);
    }

    private static BigInteger StringDecimalToBigInteger(String input) {
        return new BigInteger(input);
    }

    public static byte[] StringHexToByteArray(String x) {
        if (x.startsWith("0x")) {
            x = x.substring(2);
        }
        if (x.length() % 2 != 0) {
            x = "0" + x;
        }
        return Hex.decode(x);
    }

    public static String toJsonHex(byte[] x) {
        return "0x" + Hex.toHexString(x);
    }

    public static String toJsonHex(String x) {
        return x.startsWith("0x") ? x : "0x" + x;
    }

    public static String toJsonHex(long n) {
        return "0x" + Long.toHexString(n);
    }

    public static String toJsonHex(BigInteger n) {
        return "0x" + n.toString(16);
    }
}
