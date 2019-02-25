package org.aion.evtmgr.impl.abs;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.junit.Test;
import org.slf4j.Logger;

public class AbstractHandlerTest {
    private static final Logger LOGGER_EVENT =
            AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    private EventExecuteService eventExecuteService =
            new EventExecuteService(100, "TestEES", Thread.NORM_PRIORITY, LOGGER_EVENT);
    private IEventCallback callback = new EventCallback(eventExecuteService, LOGGER_EVENT);
    private AbstractHandler handler = new BlockHandler();

    @Test
    public void testStart() throws InterruptedException {
        AbstractHandler handler = new BlockHandler();
        handler.addEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        handler.addEvent(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        handler.onEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        handler.onEvent(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        handler.eventCallback(new EventCallback(eventExecuteService, LOGGER_EVENT));

        handler.start();
        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        handler.stop();
    }

    @Test
    public void testEventCallback() {
        handler.eventCallback(callback);
    }

    @Test
    public void testOnEvent() {
        handler.onEvent(new EventDummy());
    }

    @Test
    public void testStop() throws InterruptedException {
        handler.stop();
    }

    @Test
    public void testAddEvent() {
        AbstractHandler handler = new BlockHandler();

        boolean res = handler.addEvent(new EventDummy());
        assertTrue(res);

        boolean res2 =
                handler.addEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));
        assertTrue(res2);

        boolean res3 = handler.addEvent(null);
        assertTrue(res3);
    }

    @Test
    public void testRemoveEvent() {
        AbstractHandler handler = new BlockHandler();
        handler.addEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));

        boolean res = handler.removeEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        assertFalse(res);

        boolean res2 = handler.removeEvent(null);
        assertFalse(res2);

        boolean res3 =
                handler.removeEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));
        assertTrue(res3);
    }

    @Test
    public void testType() {
        assertEquals(BlockHandler.TYPE.BLOCK0.getValue(), handler.getType());
    }
}
