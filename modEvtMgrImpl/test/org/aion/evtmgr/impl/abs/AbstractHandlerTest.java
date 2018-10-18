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

package org.aion.evtmgr.impl.abs;

import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.junit.Test;
import org.slf4j.Logger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class AbstractHandlerTest {
    private static final Logger LOGGER_EVENT = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    private EventExecuteService eventExecuteService = new EventExecuteService(100, "TestEES", Thread.NORM_PRIORITY, LOGGER_EVENT);
    private IEventCallback callback = new EventCallback(eventExecuteService, LOGGER_EVENT);
    private AbstractHandler handler = new BlockHandler();

    @Test
    public void testStart() throws InterruptedException {
        AbstractHandler handler = new BlockHandler();
        handler.addEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        handler.addEvent(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        handler.onEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        handler.onEvent(new EventBlock(EventBlock.CALLBACK.ONBLOCK0));
        handler.eventCallback(new EventCallback(eventExecuteService, LOGGER_EVENT));

        handler.start();
        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        handler.stop();
    }

    @Test
    public void testEventCallback(){
        handler.eventCallback(callback);
    }

    @Test
    public void testOnEvent(){
        handler.onEvent(new EventDummy());
    }

    @Test
    public void testStop() throws InterruptedException {
        handler.stop();
    }

    @Test
    public void testAddEvent(){
        AbstractHandler handler = new BlockHandler();

        boolean res = handler.addEvent(new EventDummy());
        assertTrue(res);

        boolean res2 = handler.addEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));
        assertTrue(res2);

        boolean res3 = handler.addEvent(null);
        assertTrue(res3);
    }

    @Test
    public void testRemoveEvent(){
        AbstractHandler handler = new BlockHandler();
        handler.addEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));

        boolean res = handler.removeEvent(new EventBlock(EventBlock.CALLBACK.ONTRACE0));
        assertFalse(res);

        boolean res2 = handler.removeEvent(null);
        assertFalse(res2);

        boolean res3 = handler.removeEvent(new EventConsensus(EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE));
        assertTrue(res3);
    }

    @Test
    public void testType(){
        assertEquals(BlockHandler.TYPE.BLOCK0.getValue(), handler.getType());
    }
}
