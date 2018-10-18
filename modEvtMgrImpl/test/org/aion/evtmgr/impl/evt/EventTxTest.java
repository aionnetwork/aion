/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/

package org.aion.evtmgr.impl.evt;

import org.aion.evtmgr.IEvent;
import org.junit.Test;
import static junit.framework.TestCase.*;

public class EventTxTest {

    @Test
    public void testPENDINGTXSTATECHANGE0(){
        EventTx eventTx = new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0);

        assertEquals(IEvent.TYPE.TX0.getValue(), eventTx.getEventType());
        assertEquals(0, eventTx.getCallbackType());
    }

    @Test
    public void testPENDINGTXUPDATE0(){
        EventTx eventTx = new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0);

        assertEquals(IEvent.TYPE.TX0.getValue(), eventTx.getEventType());
        assertEquals(1, eventTx.getCallbackType());
    }

    @Test
    public void testPENDINGTXRECEIVED0(){
        EventTx eventTx = new EventTx(EventTx.CALLBACK.PENDINGTXRECEIVED0);

        assertEquals(IEvent.TYPE.TX0.getValue(), eventTx.getEventType());
        assertEquals(2, eventTx.getCallbackType());
    }

    @Test
    public void testTXEXECUTED0(){
        EventTx eventTx = new EventTx(EventTx.CALLBACK.TXEXECUTED0);

        assertEquals(IEvent.TYPE.TX0.getValue(), eventTx.getEventType());
        assertEquals(3, eventTx.getCallbackType());
    }

    @Test
    public void testTXBACKUP0(){
        EventTx eventTx = new EventTx(EventTx.CALLBACK.TXBACKUP0);

        assertEquals(IEvent.TYPE.TX0.getValue(), eventTx.getEventType());
        assertEquals(4, eventTx.getCallbackType());
    }

    @Test
    public void testCALLBACK(){
        assertEquals(EventTx.CALLBACK.PENDINGTXSTATECHANGE0, EventTx.CALLBACK.GETCALLBACK(0));
        assertEquals(EventTx.CALLBACK.PENDINGTXUPDATE0, EventTx.CALLBACK.GETCALLBACK(1));
        assertEquals(EventTx.CALLBACK.PENDINGTXRECEIVED0, EventTx.CALLBACK.GETCALLBACK(2));
        assertEquals(EventTx.CALLBACK.TXEXECUTED0, EventTx.CALLBACK.GETCALLBACK(3));
        assertEquals(EventTx.CALLBACK.TXBACKUP0, EventTx.CALLBACK.GETCALLBACK(4));

        assertNull(EventTx.CALLBACK.GETCALLBACK(-1));
        assertNull(EventTx.CALLBACK.GETCALLBACK(5));
    }

    @Test
    public void testState(){
        EventTx eventTx1 = new EventTx(EventTx.STATE.DROPPED0, EventTx.CALLBACK.PENDINGTXRECEIVED0);
        assertEquals(EventTx.STATE.DROPPED0.getValue(), eventTx1.getState());

        EventTx eventTx2 = new EventTx(EventTx.STATE.NEW_PENDING0, EventTx.CALLBACK.PENDINGTXRECEIVED0);
        assertEquals(EventTx.STATE.NEW_PENDING0.getValue(), eventTx2.getState());

        EventTx eventTx3 = new EventTx(EventTx.STATE.PENDING0, EventTx.CALLBACK.PENDINGTXRECEIVED0);
        assertEquals(EventTx.STATE.PENDING0.getValue(), eventTx3.getState());

        EventTx eventTx4 = new EventTx(EventTx.STATE.INCLUDED, EventTx.CALLBACK.PENDINGTXRECEIVED0);
        assertEquals(EventTx.STATE.INCLUDED.getValue(), eventTx4.getState());
    }

    @Test
    public void testStateCALLBACK(){
        assertEquals(EventTx.STATE.DROPPED0, EventTx.STATE.GETSTATE(0));
        assertEquals(EventTx.STATE.NEW_PENDING0, EventTx.STATE.GETSTATE(1));
        assertEquals(EventTx.STATE.PENDING0, EventTx.STATE.GETSTATE(2));
        assertEquals(EventTx.STATE.INCLUDED, EventTx.STATE.GETSTATE(3));

        assertNull(EventTx.STATE.GETSTATE(-1));
        assertNull(EventTx.STATE.GETSTATE(4));
    }

    @Test
    public void testIsPendingState(){
        assertFalse(EventTx.STATE.DROPPED0.isPending());
        assertTrue(EventTx.STATE.NEW_PENDING0.isPending());
        assertTrue(EventTx.STATE.PENDING0.isPending());
        assertFalse(EventTx.STATE.INCLUDED.isPending());
    }
}
