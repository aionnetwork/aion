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
import java.util.ArrayList;
import java.util.List;

import org.aion.p2p.IMsg;
import org.aion.p2p.a0.ACT;
import org.aion.p2p.a0.Node;
import org.aion.p2p.CTRL;
import org.aion.p2p.Version;
/**
 * 
 * @author chris
 *
 */
public final class ResActiveNodes implements IMsg {

    private final static byte ctrl = CTRL.NET0;

    private final static byte act = ACT.RES_ACTIVE_NODES;

    private final List<Node> nodes;

    private int count = 0;

    /**
     * id 36 bytes, ipv4 8 bytes, port 4 bytes
     */
    private final static int NODE_BYTES_LENGTH = 36 + 8 + 4;

    private final static int MAX_NODES = 40;
    
    public short getVer() {
        return Version.ZERO;
    }

    public ResActiveNodes(final List<Node> _nodes) {
        this.count = Math.min(MAX_NODES, _nodes.size());
        if (this.count > 0)
            this.nodes = _nodes.subList(0, this.count);
        else
            this.nodes = new ArrayList<Node>();
    }

    public List<Node> getNodes() {
        return this.nodes;
    }

    public static ResActiveNodes decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length == 0 || (_bytes.length - 1) % NODE_BYTES_LENGTH != 0)
            return null;
        else {
            ByteBuffer buf = ByteBuffer.wrap(_bytes);
            int count = buf.get();
            List<Node> activeNodes = new ArrayList<Node>();
            for (int i = 0; i < count; i++) {
                byte[] nodeIdBytes = new byte[36];
                buf.get(nodeIdBytes);
                byte[] ipBytes = new byte[8];
                buf.get(ipBytes);
                int port = buf.getInt();
                Node n = new Node(false, nodeIdBytes, ipBytes, port);
                activeNodes.add(n);
            }
            return new ResActiveNodes(activeNodes);
        }
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(NODE_BYTES_LENGTH * this.count + 1);
        buf.put((byte) this.count);
        for (Node n : this.nodes) {
            buf.put(n.getId());
            buf.put(n.getIp());
            buf.putInt(n.getPort());
        }
        return buf.array();
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
