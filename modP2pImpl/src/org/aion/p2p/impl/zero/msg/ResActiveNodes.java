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
import java.util.List;
import java.util.ArrayList;

import org.aion.p2p.Ctrl;
import org.aion.p2p.INode;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;

/**
 *
 * @author chris
 *
 */
public final class ResActiveNodes extends Msg {

    private final List<INode> nodes;

    private int count;

    /**
     * id 36 bytes, ipv4 8 bytes, port 4 bytes
     */
    private final static int NODE_BYTES_LENGTH = 36 + 8 + 4;

    private final static int MAX_NODES = 40;

    /**
     * @param _nodes
     *            List
     */
    public ResActiveNodes(final List<INode> _nodes) {
        super(Ver.V0, Ctrl.NET, Act.RES_ACTIVE_NODES);
        this.count = Math.min(MAX_NODES, _nodes.size());
        if (this.count > 0) {
            this.nodes = _nodes.subList(0, this.count);
        } else {
            this.nodes = new ArrayList<>();
        }
    }

    /**
     * @return List
     */
    public List<INode> getNodes() {
        return this.nodes;
    }

    /**
     * @param _bytes
     *            byte[]
     * @return ResActiveNodes
     */
    public static ResActiveNodes decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length == 0 || (_bytes.length - 1) % NODE_BYTES_LENGTH != 0) {
            return null;
        } else {

            try{

                ByteBuffer buf = ByteBuffer.wrap(_bytes);
                int count = buf.get();

                // fix bug: https://github.com/aionnetwork/aion/issues/390
                if (_bytes.length != count * NODE_BYTES_LENGTH + 1) {
                    return null;
                }

                ArrayList<INode> activeNodes = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    byte[] nodeIdBytes = new byte[36];
                    buf.get(nodeIdBytes);
                    byte[] ipBytes = new byte[8];
                    buf.get(ipBytes);
                    int port = buf.getInt();
                    INode n = new Node(false, nodeIdBytes, ipBytes, port);
                    activeNodes.add(n);
                }
                return new ResActiveNodes(activeNodes);

            } catch (Exception e) {
                System.out.println("<p2p res-active-nodes error=" + e.getMessage() + ">");
                return null;
            }
        }
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(NODE_BYTES_LENGTH * this.count + 1);
        buf.put((byte) this.count);
        for (INode n : this.nodes) {
            buf.put(n.getId());
            buf.put(n.getIp());
            buf.putInt(n.getPort());
        }
        return buf.array();
    }

}
