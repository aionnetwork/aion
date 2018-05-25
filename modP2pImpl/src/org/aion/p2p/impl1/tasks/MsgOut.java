package org.aion.p2p.impl1.tasks;

import org.aion.p2p.Msg;
import org.aion.p2p.impl1.P2pMgr.Dest;

/**
 * An outgoing message.
 */
public class MsgOut {
    private final int nodeId;
    private final String displayId;
    private final Msg msg;
    private final Dest dest;
    private final long timestamp;

    /**
     * Constructs an outgoing message.
     *
     * @param nodeId The node id.
     * @param displayId The display id.
     * @param msg The message.
     * @param dest The destination.
     */
    public MsgOut(int nodeId, String displayId, Msg msg, Dest dest) {
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.msg = msg;
        this.dest = dest;
        timestamp = System.currentTimeMillis();
    }

    public int getNodeId() {
        return this.nodeId;
    }

    public String getDisplayId() {
        return this.displayId;
    }

    public Msg getMsg() {
        return this.msg;
    }

    public Dest getDest() {
        return this.dest;
    }

    public long getTimestamp() {
        return this.timestamp;
    }
}
