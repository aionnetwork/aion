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

package org.aion.evtmgr.impl.es;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.evt.*;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class EventExecuteServiceTest {
    private static final Logger LOGGER_EVENT = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    private EventExecuteService eventExecuteService = new EventExecuteService(100, "TestEES", Thread.NORM_PRIORITY, LOGGER_EVENT);

    @Test
    public void testEES(){
        eventExecuteService.setFilter(getFilter());
        eventExecuteService.add(new EventDummy());
        eventExecuteService.add(new EventTx(EventTx.CALLBACK.PENDINGTXSTATECHANGE0));
        eventExecuteService.add(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        eventExecuteService.add(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        eventExecuteService.add(new EventConsensus(EventConsensus.CALLBACK.ON_SYNC_DONE));
        eventExecuteService.take();
        eventExecuteService.take();
        eventExecuteService.take();
        eventExecuteService.take();
        eventExecuteService.take();
    }

    @Test
    public void testTakeWithMockito(){
        EventExecuteService ees = new EventExecuteService(100, "test", Thread.NORM_PRIORITY, LOGGER_EVENT);
        assertTrue(ees.add(new EventDummy()));

        IEvent res = ees.take();
        assertEquals(IEvent.TYPE.DUMMY.getValue(), res.getEventType());
    }

    @Test
    public void testQueueFull(){
        EventExecuteService ees = new EventExecuteService(100, "test", Thread.NORM_PRIORITY, LOGGER_EVENT);
        for (int i = 0; i < 101; i++)
            ees.add(new EventDummy());
        // queue full
        assertFalse(ees.add(new EventDummy()));
    }

    @Test
    public void testEventNotRecognized(){
        assertFalse(eventExecuteService.add(new EventBlock(EventBlock.CALLBACK.ONBEST0)));
    }

    @Test
    public void testShutDown(){
        eventExecuteService.shutdown();
    }

    @Test
    public void testStart(){
        eventExecuteService.start(new testRunnable());
    }

    // simple checker tests
    @Test (expected = NullPointerException.class)
    public void testThreadNameNull(){
        EventExecuteService ees = new EventExecuteService(100, null, Thread.NORM_PRIORITY, LOGGER_EVENT);
    }

    @Test (expected = NullPointerException.class)
    public void testLogNull(){
        EventExecuteService ees = new EventExecuteService(100, "test", Thread.NORM_PRIORITY, null);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testInvalidQSize(){
        EventExecuteService ees = new EventExecuteService(10, "test", Thread.NORM_PRIORITY, LOGGER_EVENT);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testInvalidThreadPriority(){
        EventExecuteService ees = new EventExecuteService(100, "test", 15, LOGGER_EVENT);
    }

    @Test (expected = NullPointerException.class)
    public void testStartRunnableNull(){
        eventExecuteService.start(null);
    }

    @Test (expected = NullPointerException.class)
    public void testAddNullEvent(){
        eventExecuteService.add(null);
    }

    private Set<Integer> getFilter(){
        Set<Integer> s = new HashSet<>();
        // dummy
        s.add(0);
        // Tx
        s.add(256);s.add(257);s.add(258);s.add(259);s.add(260);
        // block
        s.add(512);s.add(513);s.add(514);
        // miner
        s.add(768);s.add(769);s.add(770);s.add(771);s.add(772);
        // consensus
        s.add(1024);s.add(1025);s.add(1026);

        return s;
    }

    class testRunnable implements Runnable{
        @Override
        public void run(){
            //have the take method call here
            while (true){
                IEvent event = eventExecuteService.take();
                break;
            }
            return;
        }
    }
}
