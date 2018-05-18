package org.aion.p2p.impl1;

import java.util.concurrent.LinkedBlockingQueue;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.TaskReceive.MsgIn;
import org.aion.p2p.impl1.TaskSend.MsgOut;

public class TaskStatus implements Runnable {
    private final NodeMgr nodeMgr;
    private final String selfShortId;
    private LinkedBlockingQueue<MsgOut> sendMsgQue;
    private LinkedBlockingQueue<MsgIn> receiveMsgQue;

    public TaskStatus(
            NodeMgr _nodeMgr,
            String _selfShortId,
            LinkedBlockingQueue<MsgOut> _sendMsgQue,
            LinkedBlockingQueue<MsgIn> _receiveMsgQue) {
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
