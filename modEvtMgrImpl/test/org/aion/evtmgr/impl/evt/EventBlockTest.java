package org.aion.evtmgr.impl.evt;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import org.aion.evtmgr.IEvent;
import org.junit.Test;

public class EventBlockTest {

    @Test
    public void testONBLOCK0() {
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(0, eventBlock.getCallbackType());
    }

    @Test
    public void testONTRACE0() {
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONTRACE0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(1, eventBlock.getCallbackType());
    }

    @Test
    public void testONBEST0() {
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONBEST0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(2, eventBlock.getCallbackType());
    }

    @Test
    public void testGETCALLBACK() {
        assertEquals(EventBlock.CALLBACK.ONBLOCK0, EventBlock.CALLBACK.GETCALLBACK(0));
        assertEquals(EventBlock.CALLBACK.ONTRACE0, EventBlock.CALLBACK.GETCALLBACK(1));
        assertEquals(EventBlock.CALLBACK.ONBEST0, EventBlock.CALLBACK.GETCALLBACK(2));

        assertNull(EventBlock.CALLBACK.GETCALLBACK(-1));
        assertNull(EventBlock.CALLBACK.GETCALLBACK(3));
    }
}
