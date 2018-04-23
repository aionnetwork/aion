package org.aion.p2p.impl;

import org.aion.p2p.impl1.P2pMgr;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;

public class LastThousands {


    private boolean checkPort(String host, int port) {
        boolean result = true;
        try {
            (new Socket(host, port)).close();
            result = false;
        }
        catch(IOException e) {
            // Could not connect.
        }
        return result;
    }

    @Ignore
    @Test
    public void test() throws InterruptedException {
        String nodeId = UUID.randomUUID().toString();
        String ip = "127.0.0.1";
        int port = 30303;
        int max = 1000;
        int maxPort = port + max;
        String[] testerP2p = new String[] { "p2p://" + nodeId + "@" + ip + ":" + port };
        P2pMgr tester = new P2pMgr(0,
                "",
                nodeId,
                ip,
                port,
                new String[]{},
                false,
                max,
                max,
                false,
                false,
                true,
                50);

        List<P2pMgr> examiners = new ArrayList<>();

        for(int i = port + 1; i <= maxPort; i++){
            if(checkPort(ip, i)) {
                System.out.println("examiner " + i);
                P2pMgr examiner = new P2pMgr(0,
                        "",
                        UUID.randomUUID().toString(),
                        ip,
                        i,
                        testerP2p,
                        false,
                        max,
                        max,
                        false,
                        true,
                        true,
                        50);
                examiners.add(examiner);
            }
        }

        System.out.println("examiners " + examiners.size());
        tester.run();
        for(P2pMgr examiner : examiners){
            examiner.run();
        }

        Thread.sleep(3000);

        for(P2pMgr examiner : examiners){
            assertEquals(1, examiner.getActiveNodes().size());
        }

        for(P2pMgr examiner : examiners){
            assertEquals(max, tester.getActiveNodes().size());
        }
        tester.shutdown();
        for(P2pMgr examiner : examiners){
            examiner.shutdown();
        }
    }

}