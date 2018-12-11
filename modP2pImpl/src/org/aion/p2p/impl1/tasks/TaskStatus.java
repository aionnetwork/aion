package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.util.concurrent.BlockingQueue;
import org.aion.p2p.INodeMgr;

public class TaskStatus implements Runnable {

    private final INodeMgr nodeMgr;
    private final String selfShortId;
    private final BlockingQueue<MsgOut> sendMsgQue;
    private final BlockingQueue<MsgIn> receiveMsgQue;

    public TaskStatus(
            final INodeMgr _nodeMgr,
            final String _selfShortId,
            final BlockingQueue<MsgOut> _sendMsgQue,
            final BlockingQueue<MsgIn> _receiveMsgQue) {
        this.nodeMgr = _nodeMgr;
        this.selfShortId = _selfShortId;
        this.sendMsgQue = _sendMsgQue;
        this.receiveMsgQue = _receiveMsgQue;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-ts");
        String status = this.nodeMgr.dumpNodeInfo(this.selfShortId, p2pLOG.isDebugEnabled());

        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug(status);
            p2pLOG.debug(
                    "recv queue[{}] send queue[{}]",
                    this.receiveMsgQue.size(),
                    this.sendMsgQue.size());
        } else if (p2pLOG.isInfoEnabled()) {
            p2pLOG.info(status);
        }
    }
}
