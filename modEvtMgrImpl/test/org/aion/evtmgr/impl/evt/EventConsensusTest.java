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

public class EventConsensusTest {

    @Test
    public void testON_SYNC_DONE(){
        EventConsensus eventConsensus = new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(0, eventConsensus.getCallbackType());
    }

    @Test
    public void testON_BLOCK_TEMPLATE(){
        EventConsensus eventConsensus = new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(1, eventConsensus.getCallbackType());
    }

    @Test
    public void testON_SOLUTION(){
        EventConsensus eventConsensus = new EventConsensus(EventConsensus.CALLBACK.ON_SOLUTION);

        assertEquals(IEvent.TYPE.CONSENSUS0.getValue(), eventConsensus.getEventType());
        assertEquals(2, eventConsensus.getCallbackType());
    }

    @Test
    public void testGETCALLBACK(){
        assertEquals(EventConsensus.CALLBACK.ON_SYNC_DONE, EventConsensus.CALLBACK.GETCALLBACK(0));
        assertEquals(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE, EventConsensus.CALLBACK.GETCALLBACK(1));
        assertEquals(EventConsensus.CALLBACK.ON_SOLUTION, EventConsensus.CALLBACK.GETCALLBACK(2));
        assertNull(EventConsensus.CALLBACK.GETCALLBACK(-1));
        assertNull(EventConsensus.CALLBACK.GETCALLBACK(3));
    }
}
