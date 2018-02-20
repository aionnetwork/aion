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

package org.aion.p2p.a0.msg;

import java.nio.ByteBuffer;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;

/**
 * 
 * @author chris
 * 
 */
public final class ReqHandshake implements IMsg {

    private final static byte ctrl = CTRL.NET0;

    private final static byte act = ACT.REQ_HANDSHAKE;

    private byte[] nodeId; // 36 bytes

    private int version = 0; // 4 bytes

    private byte[] ip; // 8 bytes

    private int port; // 4 bytes

    private final static int LEN = 36 + 4 + 8 + 4;

    public short getVer() {
        return P2pVer.VER0;
    }

    public ReqHandshake(final byte[] _nodeId, final int _version, final byte[] _ip, final int _port) {
        this.nodeId = _nodeId;
        this.version = _version;
        this.ip = _ip;
        this.port = _port;
    }

    public byte[] getNodeId() {
        return this.nodeId;
    }

    public int getVersion() {
        return this.version;
    }

    public byte[] getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public static ReqHandshake decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length != LEN)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.wrap(_bytes);
            byte[] _nodeId = new byte[36];
            buf.get(_nodeId);
            int _version = buf.getInt();
            byte[] _ip = new byte[8];
            buf.get(_ip);
            int _port = buf.getInt();
            return new ReqHandshake(_nodeId, _version, _ip, _port);
        }
    }

    @Override
    public byte[] encode() {
        if (this.nodeId.length != 36)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.allocate(LEN);
            buf.put(this.nodeId);
            buf.putInt(this.version);
            buf.put(this.ip);
            buf.putInt(this.port);
            return buf.array();
        }
    }

    @Override
    public byte getCtrl() {
        return ctrl;
    }

    @Override
    public byte getAct() {
        return act;
    }

}
