package org.aion.p2p.impl1;

import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Handler;
import org.aion.p2p.impl1.P2pMgr.MsgIn;

public class TaskReceive2 implements Runnable {
    private final AtomicBoolean start;
    private final LinkedBlockingQueue<MsgIn> receiveMsgQue;
    private final Map<Integer, List<Handler>> handlers;
    private final boolean showLog;

    public TaskReceive2(AtomicBoolean _start, LinkedBlockingQueue<MsgIn> _receiveMsgQue,
        Map<Integer, List<Handler>> _handlers, boolean _showLog) {
        this.start = _start;
        this.receiveMsgQue = _receiveMsgQue;
        this.handlers = _handlers;
        this.showLog = _showLog;
    }

    @Override
    public void run() {
        while (this.start.get()) {
            try {
                MsgIn mi = this.receiveMsgQue.take();

                List<Handler> hs = this.handlers.get(mi.route);
                if (hs == null) continue;
                for (Handler hlr : hs) {
                    if (hlr == null) continue;

                    try {
                        hlr.receive(mi.nid, mi.nsid, mi.msg);
                    } catch (Exception e) {
                        if (this.showLog) e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                if (this.showLog) System.out.println("<p2p task-receive-interrupted>");
                return;
            } catch (Exception e) {
                if (this.showLog) e.printStackTrace();
            }
        }
    }
}
