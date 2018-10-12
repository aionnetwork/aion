/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.log;

/**
 * Some logging utilities
 *
 * @author yao
 */
public class LogUtil {

    /**
     * Protected for now, until we can figure out why it crashes kernel, then we will integrate. See
     * : <a href= 'http://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java'>stackoverflow
     * discussion</a> and our benchmark for performance gains
     */
    final protected static char[] hexArray = "0123456789abcfef".toCharArray();
    /**
     * Guarantees a return of at max, first 8 characters, even if data is ill formatted (null,
     * shorter, longer) etc.
     *
     * @param data
     * @return
     */
    protected static final String nullString = "";

    protected static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    protected static String toHexF8Internal(byte[] data) {
        if (data == null || data.length == 0) {
            return nullString;
        }

        if (data.length > 4) {
            return toHex(data).substring(0, 8);
        } else {
            return toHex(data);
        }
    }

    /**
     * Guarantees a return of at max, last 8 characters, even if data is ill formatted (null,
     * shorter, longer) etc.
     */
    public static String toHexL8Internal(byte[] data) {
        if (data == null || data.length == 0) {
            return nullString;
        }

        if (data.length > 4) {
            return toHex(data).substring(data.length - 8, data.length);
        } else {
            return toHex(data);
        }
    }

    /**
     * Guarantees a return of at max, first 8 characters, even if data is ill formatted (null,
     * shorter, longer) etc.
     */
    public static String toHexF8(byte[] data) {
        int len = 0;
        if (data != null) {
            len = data.length;
        }

        return toHexF8Internal(data) + "... l=" + len;
    }

    /**
     * Guarantees a return of at max, last 8 characters, even if data is ill formatted (null,
     * shorter, longer) etc.
     */
    public static String toHexL8(byte[] data) {
        int len = 0;
        if (data != null) {
            len = data.length;
        }

        return "..." + toHexL8Internal(data) + " l=" + len;
    }
}
