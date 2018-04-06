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

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;

/**
 *
 * @author chris
 *
 */
public class ReqHandshake extends Msg {

    byte[] nodeId; // 36 bytes

    private int netId; // 4 bytes

    private byte[] ip; // 8 bytes

    private int port; // 4 bytes

    public final static int LEN = 36 + 4 + 8 + 4;

    public ReqHandshake(final byte[] _nodeId, int _netId, final byte[] _ip, int _port) {
        super(Ver.V0, Ctrl.NET, Act.REQ_HANDSHAKE);
        this.nodeId = _nodeId;
        this.netId = _netId;
        this.ip = _ip;
        this.port = _port;
    }

    public byte[] getNodeId() {
        return this.nodeId;
    }

    public int getNetId() {
        return this.netId;
    }

    byte[] getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    /**
     * @param _bytes byte[]
     * @return ReqHandshake
     * decode body
     */
    public static ReqHandshake decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length != LEN)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.wrap(_bytes);

            // decode node id
            byte[] nodeId = new byte[36];
            buf.get(nodeId);

            // decode net id
            int netId = buf.getInt();

            // decode ip
            byte[] ip = new byte[8];
            buf.get(ip);

            // decode port
            int port = buf.getInt();

            return new ReqHandshake(nodeId, netId, ip, port);
        }
    }

    @Override
    public byte[] encode() {
        if (this.nodeId.length != 36)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.allocate(LEN);
            buf.put(this.nodeId);
            buf.putInt(this.netId);
            buf.put(this.ip);
            buf.putInt(this.port);

            return buf.array();
        }
    }
}
