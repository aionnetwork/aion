package org.aion.util.types;

public class HexConvert {
    private static final char[] hexArray = "0123456789abcdef".toCharArray();
    private static final byte[] ZERO_BYTE_ARRAY = new byte[] {0};

    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToBytes(String s) {

        if (s == null) {
            return ZERO_BYTE_ARRAY;
        }
        if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        if ((s.length() & 1) == 1) {
            s = "0" + s;
        }

        int len = s.length();
        byte[] data = new byte[len >> 1];
        for (int i = 0; i < len; i += 2) {
            data[i >> 1] =
                    (byte)
                            ((Character.digit(s.charAt(i), 16) << 4)
                                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] stripLeadingZeroes(byte[] data) {

        if (data == null) {
            return null;
        }

        final int firstNonZero = firstNonZeroByte(data);
        switch (firstNonZero) {
            case -1:
                return ZERO_BYTE_ARRAY;
            case 0:
                return data;
            default:
                byte[] result = new byte[data.length - firstNonZero];
                System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);
                return result;
        }
    }

    private static int firstNonZeroByte(byte[] data) {
        for (int i = 0; i < data.length; ++i) {
            if (data[i] != 0) {
                return i;
            }
        }
        return -1;
    }
}
