package org.aion.p2p.impl1.tasks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Handler;
import org.slf4j.Logger;

public class TaskReceive implements Runnable {

    private final Logger p2pLOG, surveyLog;
    private final AtomicBoolean start;
    private final BlockingQueue<MsgIn> receiveMsgQue;
    private final Map<Integer, List<Handler>> handlers;

    public TaskReceive(
            final Logger p2pLOG,
            final Logger surveyLog,
            final AtomicBoolean _start,
            final BlockingQueue<MsgIn> _receiveMsgQue,
            final Map<Integer, List<Handler>> _handlers) {
        this.p2pLOG = p2pLOG;
        this.surveyLog = surveyLog;
        this.start = _start;
        this.receiveMsgQue = _receiveMsgQue;
        this.handlers = _handlers;
    }

    @Override
    public void run() {
        // for runtime survey information
        long startTime, duration;

        while (this.start.get()) {
            try {
                startTime = System.nanoTime();
                MsgIn mi = this.receiveMsgQue.take();
                duration = System.nanoTime() - startTime;
                surveyLog.info("TaskReceive: retrieve message, duration = {} ns.", duration);

                startTime = System.nanoTime();
                List<Handler> hs = this.handlers.get(mi.getRoute());
                if (hs == null) {
                    duration = System.nanoTime() - startTime;
                    surveyLog.info("TaskReceive: process message, duration = {} ns.", duration);
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
                duration = System.nanoTime() - startTime;
                surveyLog.info("TaskReceive: process message, duration = {} ns.", duration);
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
