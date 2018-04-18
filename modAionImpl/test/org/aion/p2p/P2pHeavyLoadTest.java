//package org.aion.p2p;
//
//
//import org.aion.log.AionLoggerFactory;
//import org.aion.p2p.impl.P2pMgr;
//import org.aion.p2p.impl.TestUtilities;
//import org.aion.zero.impl.sync.SyncMgr;
//import org.aion.zero.impl.sync.handler.ResBlocksHeadersHandler;
//import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
//import org.aion.zero.types.A0BlockHeader;
//import org.junit.Ignore;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.slf4j.Logger;
//
//import java.io.IOException;
//import java.net.Socket;
//import java.util.*;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
///**
// * Tests a big write, to ensure that it does not break connection
// */
//public class P2pHeavyLoadTest {
//
//    private boolean checkPort(String host, int port) {
//        boolean result = true;
//        try {
//            (new Socket(host, port)).close();
//            result = false;
//        }
//        catch(IOException e) {
//            // Could not connect.
//        }
//        return result;
//    }
//
//    public Map.Entry<P2pMgr, P2pMgr> newTwoNodeSetup() {
//        String ip = "127.0.0.1";
//
//        String id1 = UUID.randomUUID().toString();
//        String id2 = UUID.randomUUID().toString();
//
//        int port1 = 30303;
//        int port2 = 30304;
//
//        // we want node 1 to connect to node 2
//        String[] nodes = new String[] { "p2p://" + id2 + "@" + ip + ":" + port2 };
//
//        // to guarantee they don't receive the same port
//        while (port2 == port1) {
//            port2 = TestUtilities.getFreePort();
//        }
//
//        System.out.println("connector on: " + TestUtilities.formatAddr(id1, ip, port1));
//        P2pMgr connector = new P2pMgr(0,
//                "",
//                id1,
//                ip,
//                port1,
//                nodes,
//                false,
//                128,
//                128,
//                false,
//                true,
//                false);
//
//        System.out.println("receiver on: " + TestUtilities.formatAddr(id2, ip, port2));
//        P2pMgr receiver = new P2pMgr(0,
//                "",
//                id2,
//                ip,
//                port2,
//                new String[0],
//                false,
//                128,
//                128,
//                false,
//                true,
//                false);
//
//        return Map.entry(connector, receiver);
//    }
//
//    @Mock
//    SyncMgr syncManager;
//
//    Logger log = AionLoggerFactory.getLogger("TEST");
//
//    ExecutorService workers = Executors.newFixedThreadPool(8);
//
//    @Ignore
//    @Test
//    public void testHeavyWrite() throws InterruptedException {
//        String nodeId = UUID.randomUUID().toString();
//        String ip = "127.0.0.1";
//        int port = 30303;
//        int max = 1000;
//        int maxPort = port + max;
//        String[] testerP2p = new String[] { "p2p://" + nodeId + "@" + ip + ":" + port };
//        P2pMgr tester = new P2pMgr(0, "", nodeId, ip, port,  new String[]{}, false, max, max, false, false, true);
//
//        List<P2pMgr> examiners = new ArrayList<>();
//
//        for(int i = port + 1; i <= maxPort; i++){
//            if(checkPort(ip, i)) {
//                System.out.println("examiner " + i);
//                P2pMgr examiner = new P2pMgr(0, "", UUID.randomUUID().toString(), ip, i,  testerP2p, false, max, max, false, true, true);
//                examiners.add(examiner);
//            }
//        }
//
//        System.out.println("examiners " + examiners.size());
//        tester.register(Arrays.asList(new ResBlocksHeadersHandler(log, syncManager)));
//        tester.run();
//
//        for(P2pMgr examiner : examiners){
//            examiner.run();
//        }
//
//        Thread.sleep(2000L);
//        System.out.println("sending messages");
//
//        List<A0BlockHeader> headers = new ArrayList<>();
//        for (int i = 0; i < 200; i++) {
//            A0BlockHeader header = new A0BlockHeader.Builder()
//                    .build();
//            headers.add(header);
//        }
//
//        // now send a very big message
//        for (P2pMgr examiner : examiners) {
//            workers.submit(() -> examiner.send(tester.getSelfNodeIdHash(), new ResBlocksHeaders(headers)));
//        }
//
//        Thread.sleep(100000L);
//    }
//}
