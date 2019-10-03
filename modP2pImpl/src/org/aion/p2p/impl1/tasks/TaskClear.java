package org.aion.p2p.impl1.tasks;

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;
import org.slf4j.Logger;

public class TaskClear implements Runnable {

    private static final int PERIOD_CLEAR = 10000;

    private final Logger p2pLOG;
    private final INodeMgr nodeMgr;
    private final AtomicBoolean start;

    public TaskClear(final Logger p2pLOG, final INodeMgr _nodeMgr, final AtomicBoolean _start) {
        this.p2pLOG = p2pLOG;
        this.nodeMgr = _nodeMgr;
        this.start = _start;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_CLEAR);
                nodeMgr.timeoutCheck(System.currentTimeMillis());
            } catch (Exception e) {
                p2pLOG.error("TaskClear exception.", e);
            }
        }
    }
}
