package org.aion.p2p.impl.zero.msg;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.INode;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;

/** @author chris */
public final class ResActiveNodes extends Msg {

    private final List<INode> nodes;

    private int count;

    /** id 36 bytes, ipv4 8 bytes, port 4 bytes */
    private static final int NODE_BYTES_LENGTH = 36 + 8 + 4;

    private static final int MAX_NODES = 40;

    /** @param _nodes List */
    public ResActiveNodes(final List<INode> _nodes) {
        super(Ver.V0, Ctrl.NET, Act.RES_ACTIVE_NODES);
        this.count = Math.min(MAX_NODES, _nodes.size());
        if (this.count > 0) {
            this.nodes = _nodes.subList(0, this.count);
        } else {
            this.nodes = new ArrayList<>();
        }
    }

    /** @return List */
    public List<INode> getNodes() {
        return this.nodes;
    }

    /**
     * @param _bytes byte[]
     * @return ResActiveNodes
     */
    public static ResActiveNodes decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length == 0 || (_bytes.length - 1) % NODE_BYTES_LENGTH != 0) {
            return null;
        } else {

            try {

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
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("rp2p res-active-nodes error.", e);
                }
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
