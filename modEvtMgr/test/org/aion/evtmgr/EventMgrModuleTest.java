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

package org.aion.evtmgr;

import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.junit.Test;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class EventMgrModuleTest {

    @Test
    public void orderedCoverageTest() throws Throwable{
        EventMgrModule singletonEventMgrModule;
        Properties prop = new Properties();

        // try initialize with null input
        try {
            singletonEventMgrModule = EventMgrModule.getSingleton(null);
        }catch (Exception e){
            System.out.println(e.toString());
        }

        // initialize with proper input
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        singletonEventMgrModule = EventMgrModule.getSingleton(prop);

        // test getEventMgr()
        IEventMgr eventMgr = singletonEventMgrModule.getEventMgr();
        assertEquals(EventMgrA0.class, eventMgr.getClass());
        assertEquals(4, eventMgr.getHandlerList().size());
    }
}
