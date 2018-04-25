/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.zero.msg;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 * @author chris
 *
 */
public final class ResHandshake1 extends ResHandshake {

    // success(byte) + binary version len (byte)
    private static final int MIN_LEN = 2;

    private String binaryVersion;

    public ResHandshake1(boolean _success, String _binaryVersion) {
        super(_success);
        // utf-8
        this.binaryVersion = _binaryVersion.length() > 63 ? _binaryVersion.substring(0, 62) : _binaryVersion;
    }

    public String getBinaryVersion() {
        return this.binaryVersion;
    }

    public static ResHandshake1 decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length < MIN_LEN)
            return null;
        else {

            try{

                // decode success
                boolean success = _bytes[0] == 0x00 ? false : true;

                // decode binary version
                byte len = _bytes[1];
                String binaryVersion = "unknown";
                int binaryVersionBytesLen = _bytes.length;
                if(len > 0 && binaryVersionBytesLen >= MIN_LEN + len){
                    byte[] binaryVersionBytes = Arrays.copyOfRange(_bytes, MIN_LEN, MIN_LEN + len);
                    try{
                        binaryVersion = new String(binaryVersionBytes, "UTF-8");
                    } catch (UnsupportedEncodingException e) {

                    }
                }
                return new ResHandshake1(_bytes[0] == 0x01, binaryVersion);

            } catch (Exception e) {
                System.out.println("<p2p res-handshake-decode error=" + e.getMessage() + ">");
                return null;
            }
        }
    }

    @Override
    public byte[] encode() {
        byte[] superBytes = super.encode();
        byte[] binaryVersionBytes = this.binaryVersion.getBytes();
        int len = binaryVersionBytes.length;
        if(len > Byte.MAX_VALUE){
            binaryVersionBytes = Arrays.copyOfRange(binaryVersionBytes, 0 , Byte.MAX_VALUE - 1);
            len = Byte.MAX_VALUE;
        }
        ByteBuffer buf = ByteBuffer.allocate(superBytes.length + 1 + len);
        buf.put(superBytes);
        buf.put((byte)len);
        buf.put(binaryVersionBytes);
        return buf.array();
    }
}
