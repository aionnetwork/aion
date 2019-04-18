package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;

public class TaskStatus implements Runnable {

    private final INodeMgr nodeMgr;
    private final String selfShortId;
    private final BlockingQueue<MsgOut> sendMsgQue;
    private final BlockingQueue<MsgIn> receiveMsgQue;

    private static final int PERIOD_STATUS = 10000;
    private final AtomicBoolean start;

    public TaskStatus(
            final AtomicBoolean _start,
            final INodeMgr _nodeMgr,
            final String _selfShortId,
            final BlockingQueue<MsgOut> _sendMsgQue,
            final BlockingQueue<MsgIn> _receiveMsgQue) {
        this.nodeMgr = _nodeMgr;
        this.selfShortId = _selfShortId;
        this.sendMsgQue = _sendMsgQue;
        this.receiveMsgQue = _receiveMsgQue;
        this.start = _start;
    }

    @Override
    public void run() {
        p2pLOG.debug("P2p taskStatus start running.");
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_STATUS);
                String status = nodeMgr.dumpNodeInfo(selfShortId, p2pLOG.isDebugEnabled());

                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug(status);
                    p2pLOG.debug(
                            "recv queue[{}] send queue[{}]",
                            receiveMsgQue.size(),
                            sendMsgQue.size());
                } else if (p2pLOG.isInfoEnabled()) {
                    p2pLOG.info(status);
                }
            } catch (InterruptedException e) {
                p2pLOG.warn("P2p taskStatus InterruptedException! ", e);
            } catch (Exception e) {
                p2pLOG.warn("P2p taskStatus exception! ", e);
            }
        }
        p2pLOG.info("P2p taskStatus has been shut down.");
    }
}
