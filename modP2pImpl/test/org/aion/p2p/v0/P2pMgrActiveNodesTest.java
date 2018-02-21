package org.aion.p2p.v0;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class P2pMgrActiveNodesTest {

    @Test
    public void test() throws InterruptedException {

        System.gc();

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println("test active nodes");

        String seedNodeId = UUID.randomUUID().toString();
        String ip = "127.0.0.1";
        int seedNodePort = 30303;

        int m = 2;

        List<P2pMgr> nodes = new ArrayList<>();

        P2pMgr seedNode = new P2pMgr(
                seedNodeId,
                ip,
                seedNodePort,
                new String[0],
                false,
                true,
                false
        );
        seedNode.run();
        nodes.add(seedNode);

        for(int i = 0; i < 2; i++){
            P2pMgr node = new P2pMgr(
                    UUID.randomUUID().toString(),
                    ip,
                    seedNodePort + (i + 1),
                    new String[]{
                            "p2p://" + seedNodeId + "@" + ip + ":" + seedNodePort
                    },
                    false,
                    false,
                    false
            );
            nodes.add(node);
            node.run();
        }

        Thread.sleep(3000);

        // including seed node check
        for(int i = 0; i < m + 1; i++){
            assertEquals(m, nodes.get(i).getActiveNodes().size());
        }

        for(int i = 0; i < m; i++){
            nodes.get(i).shutdown();
            System.out.println("node " + i + " shutdown");
        }

        System.gc();
        System.out.println("sleep 3 s to end of test");
        Thread.sleep(3000);
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n");
    }
}