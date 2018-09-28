/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p.impl1.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.INodeMgr;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskStatusTest {

    @Mock
    private BlockingQueue<MsgOut> msgOutQue;

    @Mock
    private BlockingQueue<MsgIn> msgInQue;

    @Mock
    private INodeMgr nodeMgr;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.INFO.name());
        AionLoggerFactory.init(logMap);
    }


    @Test
    public void testRun() throws InterruptedException {

        TaskStatus ts = new TaskStatus(nodeMgr, "1", msgOutQue, msgInQue);
        assertNotNull(ts);
        when(nodeMgr.dumpNodeInfo(anyString(), anyBoolean())).thenReturn("get Status");

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());

        Thread.sleep(300);
        assertEquals("TERMINATED", t.getState().toString());
    }
}
