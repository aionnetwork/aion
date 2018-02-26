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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.api.server;

import java.nio.ByteBuffer;

public class ApiUtil {
    public final static int HASH_LEN = 8;
    public final static int HEADER_LEN = 4;
    public final static int RETHEADER_LEN = 3;

    public static byte[] toReturnHeader(int vers, int retCode, byte[] hash) {

        if (hash == null || hash.length != HASH_LEN) {
            return null;
        }

        return ByteBuffer.allocate(HASH_LEN + RETHEADER_LEN).put((byte) vers).put((byte) retCode).put((byte) 1)
                .put(hash, 0, hash.length).array();
    }

    public static byte[] toReturnHeader(int vers, int retCode, byte[] hash, byte[] result) {

        if (hash == null || result == null || hash.length != HASH_LEN) {
            return null;
        }

        return ByteBuffer.allocate(HASH_LEN + RETHEADER_LEN + result.length).put((byte) vers).put((byte) retCode)
                .put((byte) 1).put(hash, 0, hash.length).put(result, 0, result.length).array();
    }

    public static byte[] toReturnHeader(int vers, int retCode) {

        return ByteBuffer.allocate(RETHEADER_LEN).put((byte) vers).put((byte) retCode).put((byte) 0).array();
    }

    public static byte[] combineRetMsg(byte[] header, byte[] body) {
        if (header == null || body == null) {
            return null;
        }

        return ByteBuffer.allocate(header.length + body.length).put(header, 0, header.length).put(body, 0, body.length)
                .array();
    }

    public static byte[] combineRetMsg(byte[] header, byte body) {
        if (header == null) {
            return null;
        }

        return ByteBuffer.allocate(header.length + 1).put(header, 0, header.length).put(body).array();
    }

    public static byte[] getApiMsgHash(byte[] request) {
        if (request == null || request[3] == 0) {
            return null;
        }

        return ByteBuffer.allocate(HASH_LEN).put(request, HEADER_LEN, HASH_LEN).array();
    }

    public static byte[] toReturnEvtHeader(byte vers, byte[] ecb) {
        return ByteBuffer.allocate(RETHEADER_LEN + ecb.length).put(vers).put((byte) 106).put((byte) 0)
                .put(ecb, 0, ecb.length).array();
    }
}
