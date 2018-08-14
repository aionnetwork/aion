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

package org.aion.evtmgr.impl.callback;

import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.junit.Test;
import org.slf4j.Logger;

import static org.junit.Assert.assertEquals;

public class EventCallbackTest {
    private static final Logger LOGGER_EVENT = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    private EventExecuteService eventExecuteService = new EventExecuteService(100, "TestEES", Thread.NORM_PRIORITY, LOGGER_EVENT);

    @Test
    public void testConstructor(){
        EventCallback eventCallback = new EventCallback(eventExecuteService, LOGGER_EVENT);
        assertEquals(eventExecuteService, eventCallback.ees);
        assertEquals(LOGGER_EVENT, EventCallback.LOG);
    }

    @Test (expected = NullPointerException.class)
    public void testConstructorNullEES(){
        EventCallback eventCallback = new EventCallback(eventExecuteService, null);
    }

    @Test (expected = NullPointerException.class)
    public void testConstructorNullLogger(){
        EventCallback eventCallback = new EventCallback(null, LOGGER_EVENT);
    }

    @Test (expected = NullPointerException.class)
    public void testNullEvent(){
        EventCallback eventCallback = new EventCallback(eventExecuteService, LOGGER_EVENT);
        eventCallback.onEvent(null);
    }
}
