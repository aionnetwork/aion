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
 *         [0,1,2] ctrl code -> ctrl [3] action code -> action
 * 
 */

public final class Codec {

    final static int MAX_BYTES = 2 * 200 * 1024 * 1024;

    final static int FRAME_BYTES = 256;

    final static class Header {

        public final static int SIZE = 8; // bytes

        private short ver;
        private byte ctrl;
        private byte action;
        private int len;

        Header(short _ver, byte _ctrl, byte _action, int _len) {
            this.ver = _ver;
            this.ctrl = _ctrl;
            this.action = _action;
            this.len = _len < 0 ? 0 : _len;
        }

        public short getVer() {
            return this.ver;
        }

        public byte getCtrl() {
            return this.ctrl;
        }

        public byte getAction() {
            return this.action;
        }

        public int getLen() {
            return this.len;
        }

        public byte[] encode() {
            return ByteBuffer.allocate(8).putInt((ver << 16) | (ctrl << 8) | action).putInt(len).array();
        }

        public static Header decode(final byte[] _headerBytes) {
            if (_headerBytes == null || _headerBytes.length != 8)
                return null;
            else {
                ByteBuffer bb1 = ByteBuffer.wrap(_headerBytes);
                short ver = bb1.getShort();
                byte ctrl = bb1.get();
                byte action = bb1.get();
                int len = bb1.getInt();
                return new Header(ver, ctrl, action, len);
            }
        }
    }

    static class Frame {

        public final static int CHUNK = 256; // mb

        public int contextId;

        public int padding;

    }
}
