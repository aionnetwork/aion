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

package org.aion.p2p;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.aion.p2p.P2pConstant;

/**
 * @author chris
 */
public final class Header {

    public final static int LEN = 8;

    private final static int MAX_BODY_LEN_BYTES = P2pConstant.MAX_BODY_SIZE;
    private final short ver;
    private final byte ctrl;
    private final byte action;
    private int len;

    /**
     * @param _ver
     *            short
     * @param _ctrl
     *            byte
     * @param _action
     *            byte
     * @param _len
     *            byte
     */
    Header(short _ver, byte _ctrl, byte _action, int _len) {
        this.ver = _ver;
        this.ctrl = _ctrl;
        this.action = _action;
        this.len = _len < 0 ? 0 : _len;
    }

    /**
     * @return short
     */
    public short getVer() {
        return this.ver;
    }

    /**
     * @return byte
     */
    public byte getCtrl() {
        return this.ctrl;
    }

    /**
     * @return byte
     */
    public byte getAction() {
        return this.action;
    }

    /**
     * @return int
     */
    public int getRoute() {
        return (ver << 16) | (ctrl << 8) | action;
    }

    /**
     * @return int
     */
    public int getLen() {
        return this.len;
    }

    public void setLen(int _len) {
        this.len = _len;
    }

    /**
     * @return byte[]
     */
    public byte[] encode() {
        return ByteBuffer.allocate(LEN).putInt(this.getRoute()).putInt(len).array();
    }

    /**
     * @param _headerBytes
     *            byte[]
     * @return Header
     * @throws IOException
     *             when exeeds MAX_BODY_LEN_BYTES
     */
    public static Header decode(final byte[] _headerBytes) throws IOException {
        if (_headerBytes == null || _headerBytes.length != LEN)
            throw new IOException("invalid-header-bytes");
        else {
            ByteBuffer bb1 = ByteBuffer.wrap(_headerBytes);
            short ver = bb1.getShort();
            byte ctrl = bb1.get();
            byte action = bb1.get();
            int len = bb1.getInt();
            if (len > MAX_BODY_LEN_BYTES)
                throw new IOException("exceed-max-body-size");
            return new Header(ver, ctrl, action, len);
        }
    }
}