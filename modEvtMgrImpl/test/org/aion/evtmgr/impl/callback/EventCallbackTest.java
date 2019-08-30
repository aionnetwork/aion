package org.aion.evtmgr.impl.callback;

import static org.junit.Assert.assertEquals;

import org.aion.evtmgr.impl.es.EventExecuteService;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventCallbackTest {
    private static final Logger LOGGER_EVENT = LoggerFactory.getLogger("EVTMGR");
    private EventExecuteService eventExecuteService =
            new EventExecuteService(100, "TestEES", Thread.NORM_PRIORITY, LOGGER_EVENT);

    @Test
    public void testConstructor() {
        EventCallback eventCallback = new EventCallback(eventExecuteService, LOGGER_EVENT);
        assertEquals(eventExecuteService, eventCallback.ees);
        assertEquals(LOGGER_EVENT, EventCallback.LOG);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullEES() {
        EventCallback eventCallback = new EventCallback(eventExecuteService, null);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullLogger() {
        EventCallback eventCallback = new EventCallback(null, LOGGER_EVENT);
    }

    @Test(expected = NullPointerException.class)
    public void testNullEvent() {
        EventCallback eventCallback = new EventCallback(eventExecuteService, LOGGER_EVENT);
        eventCallback.onEvent(null);
    }
}
