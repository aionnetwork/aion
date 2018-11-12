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

package org.aion.p2p.impl.zero.msg;

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;

/** @author chris */
public class ReqHandshake extends Msg {

    private byte[] uniqueId = null; // 48 bytes
    byte[] nodeId; // 36 bytes

    private int netId; // 4 bytes

    private byte[] ip; // 8 bytes

    private int port; // 4 bytes

    public static final int LEN = 36 + 4 + 8 + 4;

    ReqHandshake(final byte[] _nodeId, int _netId, final byte[] _ip, int _port) {
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
     * Returns an unique identifier that contains the node identifier, IP and port number.
     *
     * @return an unique identifier that contains the node identifier, IP and port number
     */
    public byte[] getUniqueId() {
        if (uniqueId == null) {
            uniqueId = new byte[48];
            if (nodeId != null) {
                System.arraycopy(nodeId, 0, uniqueId, 0, nodeId.length);
            }
            if (ip != null) {
                System.arraycopy(ip, 0, uniqueId, 36, ip.length);
            }
            for (int i = 0; i < 4; i++) {
                uniqueId[47 - i] = (byte) (port >>> (i * 8));
            }
        }
        return uniqueId;
    }

    /**
     * @param _bytes byte[]
     * @return ReqHandshake decode body
     */
    public static ReqHandshake decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length != LEN) return null;
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
        if (this.nodeId.length != 36) return null;
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
