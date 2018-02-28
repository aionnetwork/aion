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

package org.aion.p2p.impl;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.assertEquals;

/**
 * @author chris
 */
public class P2pMgrTest {

    private String nodeId1 = UUID.randomUUID().toString();
    private String nodeId2 = UUID.randomUUID().toString();
    private String ip1 = "127.0.0.1";
    private String ip2 = "192.168.0.11";
    private int port1 = 30303;
    private int port2 = 30304;

    @Test
    public void testIgnoreSameNodeIdAsSelf() {

        String[] nodes = new String[]{
                "p2p://" + nodeId1 + "@" + ip2+ ":" + port2
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, 128, 128, false, false);
        assertEquals(p2p.getTempNodesCount(), 0);

    }

    @Test
    public void testIgnoreSameIpAndPortAsSelf(){

        String[] nodes = new String[]{
                "p2p://" + nodeId2 + "@" + ip1+ ":" + port1
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, 128, 128, false, false);
        assertEquals(0,p2p.getTempNodesCount());

    }

    @Test
    public void testTempNodes(){

        String[] nodes = new String[]{
                "p2p://" + nodeId2 + "@" + ip1+ ":" + port2,
                "p2p://" + nodeId2 + "@" + ip2+ ":" + port1,
                "p2p://" + nodeId2 + "@" + ip2+ ":" + port2,
        };

        P2pMgr p2p = new P2pMgr(nodeId1, ip1, port1, nodes, false, 128, 128,false, false);
        assertEquals(p2p.getTempNodesCount(), 3);

    }

    @Test
    public void testConnect() throws InterruptedException {

        String ip = "127.0.0.1";
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        int port1 = 30303;
        int port2 = 30304;

        P2pMgr node1 = new P2pMgr(
                id1,
                ip,
                port1,
                new String[0],
                false,
                128,
                128,
                false,
                false
        );
        node1.run();

        P2pMgr node2 = new P2pMgr(
                id2,
                ip,
                port2,
                new String[]{
                        "p2p://" + id1 + "@" + ip + ":" + port1
                },
                false,
                128,
                128,
                false,
                false
        );
        node2.run();
        Thread.sleep(5000);
        assertEquals(1, node1.getActiveNodes().size());
        assertEquals(1, node2.getActiveNodes().size());

    }

}