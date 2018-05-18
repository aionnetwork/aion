package org.aion.p2p.impl1;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;

public class TaskClear implements Runnable {
    private static final int PERIOD_CLEAR = 20000;
    private static final int TIMEOUT_OUTBOUND_NODES = 20000;

    private final P2pMgr mgr;
    private final NodeMgr nodeMgr;
    private AtomicBoolean start;

    public TaskClear(P2pMgr _mgr, NodeMgr _nodeMgr, AtomicBoolean _start) {
        this.mgr = _mgr;
        this.nodeMgr = _nodeMgr;
        this.start = _start;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_CLEAR);

                nodeMgr.timeoutInbound(this.mgr);

                Iterator outboundIt = nodeMgr.getOutboundNodes().keySet().iterator();
                while (outboundIt.hasNext()) {

                    Object obj = outboundIt.next();

                    if (obj == null) continue;

                    int nodeIdHash = (int) obj;
                    Node node = nodeMgr.getOutboundNodes().get(nodeIdHash);

                    if (node == null) continue;

                    if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_OUTBOUND_NODES) {
                        this.mgr.closeSocket(
                                node.getChannel(), "outbound-timeout node=" + node.getIdShort());
                        outboundIt.remove();
                    }
                }

                nodeMgr.timeoutActive(this.mgr);

            } catch (Exception e) {
            }
        }
    }
}
