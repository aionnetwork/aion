/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 *
 * <p>****************************************************************************
 */
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
