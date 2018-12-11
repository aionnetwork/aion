package org.aion.evtmgr.impl.evt;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import java.util.LinkedList;
import org.aion.evtmgr.IEvent;
import org.junit.Test;

public class EventDummyTest {

    @Test
    public void testDummyClass() {
        EventDummy eventDummy = new EventDummy();

        assertEquals(IEvent.TYPE.DUMMY.getValue(), eventDummy.getEventType());
        assertEquals(0, eventDummy.getCallbackType());
        assertEquals(IEvent.TYPE.DUMMY.getValue(), EventDummy.getTypeStatic());
    }

    @Test
    public void testDummyFunctions() {
        EventDummy eventDummy = new EventDummy();

        eventDummy.setFuncArgs(new LinkedList<>());
        assertNull(eventDummy.getFuncArgs());
    }
}
