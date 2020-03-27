package org.aion.p2p.impl1;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

public class TaskClearTest {
    @Mock private Logger p2pLOG;

    @Mock private INodeMgr nodeMgr;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(timeout = 20_000)
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskClear tc = new TaskClear(p2pLOG, nodeMgr, atb);
        assertNotNull(tc);

        Thread t = new Thread(tc);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        while (!t.getState().toString().equals("TERMINATED")) {
            Thread.sleep(100);
        }
    }
}
