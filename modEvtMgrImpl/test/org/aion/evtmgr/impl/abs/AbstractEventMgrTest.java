package org.aion.evtmgr.impl.abs;

import static junit.framework.TestCase.assertEquals;

import java.util.List;
import java.util.Properties;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.handler.TxHandler;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.junit.Test;

public class AbstractEventMgrTest {
    private Properties properties = new Properties();
    private EventMgrA0 evtManager = new EventMgrA0(properties);

    @Test
    public void testStart() {
        evtManager.start();
    }

    @Test
    public void testShutdown() throws InterruptedException {
        // this will take 10s
        evtManager.shutDown();
    }

    @Test
    public void testGetHandler() {
        IHandler res = evtManager.getHandler(1);
        IHandler expectedHandler = new TxHandler();
        assertEquals(expectedHandler.getType(), res.getType());

        IHandler res2 = evtManager.getHandler(0);
        IHandler expectedHandler2 = null;
        assertEquals(expectedHandler2, res2);

        IHandler res3 = evtManager.getHandler(5);
        IHandler expectedHandler3 = null;
        assertEquals(expectedHandler3, res3);
    }

    @Test
    public void testGetHandlerList() {
        List<IHandler> res = evtManager.getHandlerList();
        assertEquals(4, res.size());
    }
}
