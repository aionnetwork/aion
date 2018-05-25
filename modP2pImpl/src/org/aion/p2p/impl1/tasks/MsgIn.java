package org.aion.p2p.impl1.tasks;

/**
 * An incoming message.
 */
public class MsgIn {
    private final int nodeId;
    private final String displayId;
    private final int route;
    private final byte[] msg;

    /**
     * Constructs an incoming message.
     *
     * @param nodeId The node id.
     * @param displayId The display id.
     * @param route The route.
     * @param msg The message.
     */
    public MsgIn(int nodeId, String displayId, int route, byte[] msg) {
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.route = route;
        this.msg = msg;
    }

    public int getNodeId() {
        return this.nodeId;
    }

    public String getDisplayId() {
        return this.displayId;
    }

    public int getRoute() {
        return this.route;
    }

    public byte[] getMsg() {
        return this.msg;
    }
}
