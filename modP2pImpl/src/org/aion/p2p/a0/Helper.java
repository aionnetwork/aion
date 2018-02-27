/*******************************************************************************
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
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.p2p.a0;

import java.nio.ByteBuffer;

/**
 * 
 * @author chris
 *
 */
public final class Helper {
    protected final static char[] decimalArray = "0123456789".toCharArray();
    private static final int SIZE_BYTES_IPV4 = 8;

    /**
     * TODO: need interface to cover these 2 methods
     */
    public static byte[] ipStrToBytes(final String _ip) {
        ByteBuffer bb8 = ByteBuffer.allocate(8);
        String[] frags = _ip.split("\\.");
        for(String frag : frags) {
            short ipFrag = 0;
            try {
                ipFrag = Short.parseShort(frag);
            } catch (NumberFormatException e) {

            }
            bb8.putShort(ipFrag);
        }
        return bb8.array();
    }

    public static String ipBytesToStr(final byte[] _ip) {
        if(_ip == null || _ip.length != SIZE_BYTES_IPV4)
            return "";
        else {
            short[] shorts = new short[_ip.length/2];
            ByteBuffer.wrap(_ip).asShortBuffer().get(shorts);

            String ip = "";
            for (int i = 0; i < shorts.length; i++) {
                ip += shorts[i] + (i < shorts.length - 1 ? "." : "");
            }

            return ip;
        }
    }
   
    public static String bytesToDecimal(byte[] bytes) {
        char[] decimalChars = new char[bytes.length * 4];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            decimalChars[j * 4] = decimalArray[v / 100];
            decimalChars[j * 4 + 1] = decimalArray[(v / 10) % 10];
            decimalChars[j * 4 + 2] = decimalArray[v % 10];
            decimalChars[j * 4 + 3] = ' ';
        }
        return new String(decimalChars);
    }
}
