package org.aion.evtmgr.impl.evt;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import org.aion.evtmgr.IEvent;
import org.junit.Test;

public class EventConsensusTest {

    @Test
    public void testON_SYNC_DONE() {
        EventConsensus eventConsensus = new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(0, eventConsensus.getCallbackType());
    }

    @Test
    public void testON_BLOCK_TEMPLATE() {
        EventConsensus eventConsensus =
                new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(1, eventConsensus.getCallbackType());
    }

    @Test
    public void testON_SOLUTION() {
        EventConsensus eventConsensus = new EventConsensus(EventConsensus.CALLBACK.ON_SOLUTION);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(2, eventConsensus.getCallbackType());
    }

    @Test
    public void testGETCALLBACK() {
        assertEquals(EventConsensus.CALLBACK.ON_SYNC_DONE, EventConsensus.CALLBACK.GETCALLBACK(0));
        assertEquals(
                EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE, EventConsensus.CALLBACK.GETCALLBACK(1));
        assertEquals(EventConsensus.CALLBACK.ON_SOLUTION, EventConsensus.CALLBACK.GETCALLBACK(2));
        assertNull(EventConsensus.CALLBACK.GETCALLBACK(-1));
        assertNull(EventConsensus.CALLBACK.GETCALLBACK(3));
    }
}
