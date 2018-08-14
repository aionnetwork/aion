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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.p2p.Handler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskRecvTest {

    @Mock
    private BlockingQueue<MsgIn> recvMsgQue;

    @Mock
    private Handler h;

    @Mock
    private Map<Integer, List<Handler>> handler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevel.TRACE.name());
        AionLoggerFactory.init(logMap);
    }


    @Test
    public void testRun() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskReceive ts = new TaskReceive(atb, recvMsgQue, handler);
        assertNotNull(ts);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);
        atb.set(false);
        Thread.sleep(2000);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test
    public void testRunMsgIn() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskReceive ts = new TaskReceive(atb, recvMsgQue, handler);
        assertNotNull(ts);

        int route = 1;
        MsgIn mi = new MsgIn(1, "1", route, new byte[0]);
        assertNotNull(mi);
        when(recvMsgQue.take()).thenReturn(mi);
        when(handler.get(route)).thenReturn(null);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);

        List<Handler> hdlr = new ArrayList<>();
        hdlr.add(null);
        hdlr.add(h);

        when(handler.get(route)).thenReturn(hdlr);

        atb.set(false);
        Thread.sleep(30);
        assertEquals("TERMINATED", t.getState().toString());
    }

    @Test (expected = Exception.class)
    public void testRunMsgIn2() throws InterruptedException {
        AtomicBoolean atb = new AtomicBoolean(true);
        TaskReceive ts = new TaskReceive(atb, recvMsgQue, handler);
        assertNotNull(ts);

        int route = 1;
        MsgIn mi = new MsgIn(1, "1", route, new byte[0]);
        assertNotNull(mi);
        when(recvMsgQue.take()).thenReturn(mi);
        when(handler.get(route)).thenReturn(null);

        Thread t = new Thread(ts);
        t.start();
        assertTrue(t.isAlive());
        Thread.sleep(10);

        List<Handler> hdlr = new ArrayList<>();
        hdlr.add(null);
        hdlr.add(h);

        when(handler.get(route)).thenReturn(hdlr);
        doThrow(new Exception("test exception!")).when(h).receive(anyInt(), anyString(), any());

        atb.set(false);
        Thread.sleep(30);
        assertEquals("TERMINATED", t.getState().toString());
    }

}
