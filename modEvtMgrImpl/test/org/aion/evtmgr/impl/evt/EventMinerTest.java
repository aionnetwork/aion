package org.aion.evtmgr.impl.evt;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import org.aion.evtmgr.IEvent;
import org.junit.Test;

public class EventMinerTest {

    @Test
    public void testMININGSTARTED() {
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.MININGSTARTED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(0, eventMiner.getCallbackType());
    }

    @Test
    public void testMININGSTOPPED() {
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.MININGSTOPPED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(1, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMININGSTARTED() {
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMININGSTARTED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(2, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMINED() {
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMINED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(3, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMININGCANCELED() {
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMININGCANCELED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(4, eventMiner.getCallbackType());
    }

    @Test
    public void testGETCALLBACK() {
        assertEquals(EventMiner.CALLBACK.MININGSTARTED, EventMiner.CALLBACK.GETCALLBACK(0));
        assertEquals(EventMiner.CALLBACK.MININGSTOPPED, EventMiner.CALLBACK.GETCALLBACK(1));
        assertEquals(EventMiner.CALLBACK.BLOCKMININGSTARTED, EventMiner.CALLBACK.GETCALLBACK(2));
        assertEquals(EventMiner.CALLBACK.BLOCKMINED, EventMiner.CALLBACK.GETCALLBACK(3));
        assertEquals(EventMiner.CALLBACK.BLOCKMININGCANCELED, EventMiner.CALLBACK.GETCALLBACK(4));

        assertNull(EventMiner.CALLBACK.GETCALLBACK(-1));
        assertNull(EventMiner.CALLBACK.GETCALLBACK(5));
    }
}
