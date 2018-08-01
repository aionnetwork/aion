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

public class EventMinerTest {

    @Test
    public void testMININGSTARTED(){
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.MININGSTARTED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(0, eventMiner.getCallbackType());
    }

    @Test
    public void testMININGSTOPPED(){
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.MININGSTOPPED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(1, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMININGSTARTED(){
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMININGSTARTED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(2, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMINED(){
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMINED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(3, eventMiner.getCallbackType());
    }

    @Test
    public void testBLOCKMININGCANCELED(){
        EventMiner eventMiner = new EventMiner(EventMiner.CALLBACK.BLOCKMININGCANCELED);

        assertEquals(IEvent.TYPE.MINER0.getValue(), eventMiner.getEventType());
        assertEquals(4, eventMiner.getCallbackType());
    }

    @Test
    public void testGETCALLBACK(){
        assertEquals(EventMiner.CALLBACK.MININGSTARTED ,EventMiner.CALLBACK.GETCALLBACK(0));
        assertEquals(EventMiner.CALLBACK.MININGSTOPPED ,EventMiner.CALLBACK.GETCALLBACK(1));
        assertEquals(EventMiner.CALLBACK.BLOCKMININGSTARTED ,EventMiner.CALLBACK.GETCALLBACK(2));
        assertEquals(EventMiner.CALLBACK.BLOCKMINED ,EventMiner.CALLBACK.GETCALLBACK(3));
        assertEquals(EventMiner.CALLBACK.BLOCKMININGCANCELED ,EventMiner.CALLBACK.GETCALLBACK(4));

        assertNull(EventMiner.CALLBACK.GETCALLBACK(-1));
        assertNull(EventMiner.CALLBACK.GETCALLBACK(5));
    }

}
