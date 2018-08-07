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

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.handler.TxHandler;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.junit.Test;
import java.util.List;
import java.util.Properties;

import static junit.framework.TestCase.assertEquals;

public class AbstractEventMgrTest {
    private Properties properties = new Properties();
    private EventMgrA0 evtManager = new EventMgrA0(properties);

    @Test
    public void testStart(){
        evtManager.start();
    }

    @Test
    public void testShutdown() throws InterruptedException {
        // this will take 10s
        evtManager.shutDown();
    }

    @Test
    public void testGetHandler(){
        IHandler res = evtManager.getHandler(1);
        IHandler expectedHandler = new TxHandler();
        assertEquals(expectedHandler.getType(), res.getType());

        IHandler res2 = evtManager.getHandler(0);
        IHandler expectedHandler2 = null;
        assertEquals(expectedHandler2, res2);

        IHandler res3 = evtManager.getHandler(5);
        IHandler expectedHandler3 = null;
        assertEquals(expectedHandler3, res3);
    }

    @Test
    public void testGetHandlerList(){
        List<IHandler> res = evtManager.getHandlerList();
        assertEquals(4, res.size());
    }
}
