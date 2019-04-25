package org.aion.p2p.impl;

import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.zero.msg.ReqActiveNodes;
import org.slf4j.Logger;

/** @author chris */
public final class TaskRequestActiveNodes implements Runnable {

    private final IP2pMgr mgr;

    private final Logger p2pLOG;

    private static final ReqActiveNodes reqActiveNodesMsg = new ReqActiveNodes();

    public TaskRequestActiveNodes(final IP2pMgr _mgr, final Logger p2pLOG) {
        this.mgr = _mgr;
        this.p2pLOG = p2pLOG;
    }

    @Override
    public void run() {
        INode node = mgr.getRandom();
        if (node != null) {
            Thread.currentThread().setName("p2p-reqNodes");
            if (p2pLOG.isTraceEnabled()) {
                p2pLOG.trace("TaskRequestActiveNodes: {}", node.toString());
            }
            this.mgr.send(node.getIdHash(), node.getIdShort(), reqActiveNodesMsg);
        }
    }
}
