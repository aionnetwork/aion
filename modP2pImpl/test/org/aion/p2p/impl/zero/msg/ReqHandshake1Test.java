/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.junit.Before;
import org.junit.Test;

/**
 * @author chris
 */
public class ReqHandshake1Test {

    private byte[] validNodeId = UUID.randomUUID().toString().getBytes();

    private int netId = ThreadLocalRandom.current().nextInt();

    private byte[] invalidNodeId = UUID.randomUUID().toString().substring(0, 34).getBytes();

    private int port = ThreadLocalRandom.current().nextInt();

    private String randomIp = ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256) + "." +
            ThreadLocalRandom.current().nextInt(0,256);

    private byte[] randomRevision;

    private List<Short> randomVersions;


    @Before
    public void ReqHandshake2Test() {

        randomRevision = new byte[Byte.MAX_VALUE];
        ThreadLocalRandom.current().nextBytes(randomRevision);
        randomVersions = new ArrayList<>();
        for(byte i = 0; i < 127; i ++){
            randomVersions.add((short)ThreadLocalRandom.current().nextInt(Short.MAX_VALUE + 1));
        }

    }

    @Test
    public void testRoute() {
        System.out.println("randomRevision " + randomRevision);
        ReqHandshake1 req = new ReqHandshake1(validNodeId, netId, Node.ipStrToBytes(randomIp), port, randomRevision, randomVersions);
        assertEquals(Ver.V0, req.getHeader().getVer());
        assertEquals(Ctrl.NET, req.getHeader().getCtrl());
        assertEquals(Act.REQ_HANDSHAKE, req.getHeader().getAction());
    }

    @Test
    public void testValidEncodeDecode() {

        ReqHandshake1 req1 = new ReqHandshake1(validNodeId, netId, Node.ipStrToBytes(randomIp), port, randomRevision, randomVersions);
        byte[] bytes = req1.encode();

        ReqHandshake1 req2 = ReqHandshake1.decode(bytes);
        assertNotNull(req2.getNodeId());
        assertArrayEquals(req1.getNodeId(), req2.getNodeId());
        assertArrayEquals(req1.getIp(), req2.getIp());
        assertEquals(req1.getNetId(), req2.getNetId());
        assertEquals(req1.getPort(), req2.getPort());

    }

    @Test
    public void testInvalidEncodeDecode() {

        ReqHandshake1 req1 = new ReqHandshake1(invalidNodeId, netId, Node.ipStrToBytes(randomIp), port, randomRevision, randomVersions);
        byte[] bytes = req1.encode();
        assertNull(bytes);
    }



}
