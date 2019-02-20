package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Handler;

public class TaskReceive implements Runnable {

    private final AtomicBoolean start;
    private final BlockingQueue<MsgIn> receiveMsgQue;
    private final Map<Integer, List<Handler>> handlers;

    public TaskReceive(
            final AtomicBoolean _start,
            final BlockingQueue<MsgIn> _receiveMsgQue,
            final Map<Integer, List<Handler>> _handlers) {
        this.start = _start;
        this.receiveMsgQue = _receiveMsgQue;
        this.handlers = _handlers;
    }

    @Override
    public void run() {
        while (this.start.get()) {
            try {
                MsgIn mi = this.receiveMsgQue.take();

                List<Handler> hs = this.handlers.get(mi.getRoute());
                if (hs == null) {
                    continue;
                }
                for (Handler hlr : hs) {
                    if (hlr == null) {
                        continue;
                    }

                    try {
                        hlr.receive(mi.getNodeId(), mi.getDisplayId(), mi.getMsg());
                    } catch (Exception e) {
                        if (p2pLOG.isDebugEnabled()) {
                            p2pLOG.debug("TaskReceive exception.", e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                p2pLOG.error("TaskReceive interrupted.", e);
                return;
            } catch (Exception e) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("TaskReceive exception.", e);
                }
            }
        }
    }
}
