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

import static org.aion.p2p.Header.LEN;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;
import org.aion.p2p.Header;
import org.aion.p2p.impl1.tasks.ChannelBuffer.RouteStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ChannelBufferTest {

    ChannelBuffer cb;
    Random r;

    @Mock
    Header header;

    Header expectHeader;

    @Before
    public void Setup() {
        MockitoAnnotations.initMocks(this);

        Map<String, String> logMap = new HashMap<>();
        logMap.put(LogEnum.P2P.name(), LogLevels.TRACE.name());
        AionLoggerFactory.init(logMap);

        cb = new ChannelBuffer();
        r = new Random();
    }

    private ByteBuffer genBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(r.nextInt(LEN << 1));
        int len = buffer.remaining() - LEN;
        expectHeader = Header.decode(genHeader(len < 0 ? 0 : len));
        //expectHeader.setLen(r.nextInt(P2pConstant.MAX_BODY_SIZE));

        buffer.put(expectHeader.encode(), 0, len < 0 ? buffer.remaining() : LEN);
        byte[] rByte = new byte[expectHeader.getLen()];
        r.nextBytes(rByte);
        buffer.put(rByte, 0, buffer.remaining() >= rByte.length ? rByte.length : buffer.remaining()).flip();

        return buffer;
    }

    private byte[] genHeader(int len) {
        when(header.getRoute()).thenReturn(r.nextInt());
        return ByteBuffer.allocate(LEN).putInt(header.getRoute()).putInt(len).array();
    }

    @Test
    public void testNodeIdHash() {
        int id = r.nextInt();
        cb.setNodeIdHash(id);
        assertEquals(id, cb.getNodeIdHash());
    }

    @Test
    public void testDisplayId() {
        String id = UUID.randomUUID().toString();
        cb.setDisplayId(id);
        assertEquals(id, cb.getDisplayId());
    }

    @Test
    public void testRefreshHeader() {
        cb.header = header;
        cb.refreshHeader();
        assertNull(cb.header);
    }

    @Test
    public void testRefreshBody() {
        cb.body = UUID.randomUUID().toString().getBytes();
        cb.refreshBody();
        assertNull(cb.body);
    }

    @Test
    public void testReadHead() {
        for (int i=0 ; i<100 ; i++) {
            cb.refreshHeader();
            ByteBuffer bb = genBuffer();
            cb.readHead(bb);
            if (bb.array().length >= LEN) {
                assertArrayEquals(expectHeader.encode(), cb.header.encode());
            } else {
                assertNull(cb.header);
            }
        }
    }

    @Test
    public void TestHeaderNotCompleted() {
        assertTrue(cb.isHeaderNotCompleted());
        cb.header = header;
        assertFalse(cb.isHeaderNotCompleted());
    }

    @Test
    public void TestBodyNotCompleted() {
        when(header.getLen()).thenReturn(UUID.randomUUID().toString().getBytes().length);
        assertTrue(cb.isBodyNotCompleted());
        cb.header = header;
        assertTrue(cb.isBodyNotCompleted());
        cb.body = UUID.randomUUID().toString().getBytes();
        assertFalse(cb.isBodyNotCompleted());
    }

    @Test
    public void testReadBody() {
        for (int i=0 ; i<100 ; i++) {
            cb.refreshHeader();
            cb.refreshBody();
            ByteBuffer bb = genBuffer();
            cb.readHead(bb);
            if (bb.array().length >= LEN) {
                assertArrayEquals(expectHeader.encode(), cb.header.encode());
                cb.readBody(bb);
                assertNotNull(cb.body);
                assertEquals(cb.header.getLen(), cb.body.length);
            } else {
                assertNull(cb.header);
            }
        }
    }

    @Test
    public void testReadBodyNotCompleted() {
        ByteBuffer bb = genBuffer();
        cb.readBody(bb);
        assertNull(cb.body);
    }

    @Test
    public void testShouldRoute() throws InterruptedException {
        assertTrue(cb.shouldRoute(1, 1));
        assertTrue(cb.shouldRoute(1, 1));
        assertFalse(cb.shouldRoute(1, 1));
        Thread.sleep(1001);
        assertTrue(cb.shouldRoute(1, 2));

        cb.shouldRoute(1, 2);
        cb.shouldRoute(1, 2);

        RouteStatus rs = cb.getRouteCount(1);
        assertNotNull(rs);
        assertEquals(2, rs.count);
    }
}
