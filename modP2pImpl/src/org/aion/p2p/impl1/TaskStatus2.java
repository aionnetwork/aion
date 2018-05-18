package org.aion.p2p.impl1;

import java.util.concurrent.LinkedBlockingQueue;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.P2pMgr.MsgIn;
import org.aion.p2p.impl1.P2pMgr.MsgOut;

public class TaskStatus2 implements Runnable {
    private final NodeMgr nodeMgr;
    private final String selfShortId;
    private LinkedBlockingQueue<MsgOut> sendMsgQue;
    private LinkedBlockingQueue<MsgIn> receiveMsgQue;

    public TaskStatus2(NodeMgr _nodeMgr, String _selfShortId,
        LinkedBlockingQueue<MsgOut> _sendMsgQue, LinkedBlockingQueue<MsgIn> _receiveMsgQue) {
        this.nodeMgr = _nodeMgr;
        this.selfShortId = _selfShortId;
        this.sendMsgQue = _sendMsgQue;
        this.receiveMsgQue = _receiveMsgQue;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("p2p-ts");
        String status = this.nodeMgr.dumpNodeInfo(this.selfShortId);
        System.out.println(status);
        System.out.println(
            "--------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println(
            "recv queue ["
                + this.receiveMsgQue.size()
                + "] send queue ["
                + this.sendMsgQue.size()
                + "]\n");
    }
}
