package org.aion.p2p.impl1;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.INodeMgr;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskStatusTest {
    @Mock private Logger p2pLOG;
    private Logger surveyLog;

    @Mock private BlockingQueue<MsgOut> msgOutQue;

    @Mock private BlockingQueue<MsgIn> msgInQue;

    @Mock private INodeMgr nodeMgr;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        surveyLog = spy(LoggerFactory.getLogger("SURVEY"));
        doNothing().when(surveyLog).info(any());
    }

    @Test(timeout = 10_000)
    public void testRun() throws InterruptedException {

        final AtomicBoolean ab = new AtomicBoolean(true);

        TaskStatus ts = new TaskStatus(p2pLOG, surveyLog, ab, nodeMgr, "1", msgOutQue, msgInQue);
        assertNotNull(ts);
        when(nodeMgr.dumpNodeInfo(anyString(), anyBoolean())).thenReturn("get Status");

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());

        ab.set(false);
        while (!t.getState().toString().contains("TERMINATED")) {
            Thread.sleep(10);
        }
    }
}
