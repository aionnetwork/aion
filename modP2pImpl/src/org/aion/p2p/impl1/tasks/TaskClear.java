package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;

public class TaskClear implements Runnable {

    private static final int PERIOD_CLEAR = 10000;

    private final INodeMgr nodeMgr;
    private final AtomicBoolean start;

    public TaskClear(final INodeMgr _nodeMgr, final AtomicBoolean _start) {
        this.nodeMgr = _nodeMgr;
        this.start = _start;
    }

    @Override
    public void run() {
        while (start.get()) {
            try {
                Thread.sleep(PERIOD_CLEAR);
                nodeMgr.timeoutCheck();
            } catch (Exception e) {
                p2pLOG.error("TaskClear exception.", e);
            }
        }
    }
}
