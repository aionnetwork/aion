/*
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
 */

package org.aion.p2p.impl.zero.msg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.aion.p2p.Ctrl;
import org.aion.p2p.INode;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.junit.Test;

/**
 * @author chris
 */
public class ResActiveNodesTest {

    private Node randomNode() {
        return new Node(
            ThreadLocalRandom.current().nextBoolean(),
            UUID.randomUUID().toString().getBytes(),
            Node.ipStrToBytes(
                ThreadLocalRandom.current().nextInt(0, 256)
                    + "."
                    + ThreadLocalRandom.current().nextInt(0, 256)
                    + "."
                    + ThreadLocalRandom.current().nextInt(0, 256)
                    + "."
                    + ThreadLocalRandom.current().nextInt(0, 256)),
            ThreadLocalRandom.current().nextInt());
    }

    @Test
    public void testRoute() {

        ResActiveNodes res = new ResActiveNodes(new ArrayList<>());
        assertEquals(Ver.V0, res.getHeader().getVer());
        assertEquals(Ctrl.NET, res.getHeader().getCtrl());
        assertEquals(Act.RES_ACTIVE_NODES, res.getHeader().getAction());
    }

    @Test
    public void testEncodeDecode() {

        int m = ThreadLocalRandom.current().nextInt(0, 20);
        List<INode> srcNodes = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            srcNodes.add(randomNode());
        }

        ResActiveNodes res = ResActiveNodes.decode(new ResActiveNodes(srcNodes).encode());
        assertEquals(res.getNodes().size(), m);
        List<INode> tarNodes = res.getNodes();
        for (int i = 0; i < m; i++) {

            INode srcNode = srcNodes.get(i);
            INode tarNode = tarNodes.get(i);

            assertArrayEquals(srcNode.getId(), tarNode.getId());
            assertEquals(srcNode.getIdHash(), tarNode.getIdHash());
            assertArrayEquals(srcNode.getIp(), tarNode.getIp());

            assertEquals(srcNode.getIpStr(), tarNode.getIpStr());
            assertEquals(srcNode.getPort(), tarNode.getPort());
        }
    }

    // Only 40 Active Nodes are returned at MAX
    @Test
    public void testMaxActive() {

        List<INode> srcNodes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            srcNodes.add(randomNode());
        }

        ResActiveNodes res = ResActiveNodes.decode(new ResActiveNodes(srcNodes).encode());
        assertEquals(40, res.getNodes().size());

        List<INode> tarNodes = res.getNodes();
        for (int i = 0; i < 40; i++) {

            INode srcNode = srcNodes.get(i);
            INode tarNode = tarNodes.get(i);

            assertArrayEquals(srcNode.getId(), tarNode.getId());
            assertEquals(srcNode.getIdHash(), tarNode.getIdHash());
            assertArrayEquals(srcNode.getIp(), tarNode.getIp());

            assertEquals(srcNode.getIpStr(), tarNode.getIpStr());
            assertEquals(srcNode.getPort(), tarNode.getPort());
        }
    }

    @Test
    public void testDecodeNull() {
        assertNull(ResHandshake.decode(null));
        assertNull(ResHandshake.decode(new byte[0]));
    }
}
