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
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class EventBlockTest {

    @Test
    public void testONBLOCK0(){
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONBLOCK0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(0, eventBlock.getCallbackType());
    }

    @Test
    public void testONTRACE0(){
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONTRACE0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(1, eventBlock.getCallbackType());
    }

    @Test
    public void testONBEST0(){
        EventBlock eventBlock = new EventBlock(EventBlock.CALLBACK.ONBEST0);

        assertEquals(IEvent.TYPE.BLOCK0.getValue(), eventBlock.getEventType());
        assertEquals(2, eventBlock.getCallbackType());
    }

    @Test
    public void testGETCALLBACK(){
        assertEquals(EventBlock.CALLBACK.ONBLOCK0, EventBlock.CALLBACK.GETCALLBACK(0));
        assertEquals(EventBlock.CALLBACK.ONTRACE0, EventBlock.CALLBACK.GETCALLBACK(1));
        assertEquals(EventBlock.CALLBACK.ONBEST0, EventBlock.CALLBACK.GETCALLBACK(2));

        assertNull(EventBlock.CALLBACK.GETCALLBACK(-1));
        assertNull(EventBlock.CALLBACK.GETCALLBACK(3));
    }
}
