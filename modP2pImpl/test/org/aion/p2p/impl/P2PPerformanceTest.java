package org.aion.p2p.impl;

import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import org.aion.p2p.impl1.P2pMgr;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

public class P2PPerformanceTest {

    public Map.Entry<P2pMgr, P2pMgr> newTwoNodeSetup() {
        String ip = "127.0.0.1";

        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        int port1 = 30303;
        int port2 = 30304;

        // we want node 1 to connect to node 2
        String[] nodes = new String[] { "p2p://" + id2 + "@" + ip + ":" + port2 };

        // to guarantee they don't receive the same port
        while (port2 == port1) {
            port2 = TestUtilities.getFreePort();
        }

        System.out.println("connector on: " + TestUtilities.formatAddr(id1, ip, port1));
        P2pMgr connector = new P2pMgr(0,
                "",
                id1,
                ip,
                port1,
                nodes,
                false,
                128,
                128,
                false,
                true,
                false,
                false,
                "",
                50);

        System.out.println("receiver on: " + TestUtilities.formatAddr(id2, ip, port2));
        P2pMgr receiver = new P2pMgr(0,
                "",
                id2,
                ip,
                port2,
                new String[0],
                false,
                128,
                128,
                false,
                true,
                false,
                false,
                "",
                50);

        return Map.entry(connector, receiver);
    }

    private static class PingMsg extends Msg {
        public PingMsg() {
            super((short) 0, (byte) 64, (byte) 0);
        }

        @Override
        public Header getHeader() {
            return super.getHeader();
        }

        @Override
        public byte[] encode() {
            return new byte[0];
        }
    }

    private static class PongMsg extends Msg {
        public PongMsg() {
            super((short) 0, (byte) 64, (byte) 1);
        }

        @Override
        public Header getHeader() {
            return super.getHeader();
        }

        @Override
        public byte[] encode() {
            return new byte[0];
        }
    }

    private static final PingMsg PING_MSG = new PingMsg();
    private static final PongMsg PONG_MSG = new PongMsg();

    private static class PingHandler extends Handler {

        private final P2pMgr p2pMgr;

        public PingHandler(P2pMgr p2pMgr) {
            super((short) 0, (byte) 64, (byte) 0);
            this.p2pMgr = p2pMgr;
        }

        @Override
        public Header getHeader() {
            return super.getHeader();
        }

        @Override
        public void receive(int _id, String _displayId, byte[] _msg) {
            System.out.println("ping!");
            this.p2pMgr.send(_id, PONG_MSG);
        }
    }

    private static class PongHandler extends Handler {

        private final P2pMgr p2pMgr;

        public PongHandler(P2pMgr p2pMgr) {
            super((short) 0, (byte) 64, (byte) 1);
            this.p2pMgr = p2pMgr;
        }

        @Override
        public Header getHeader() {
            return super.getHeader();
        }

        @Override
        public void receive(int _id, String _displayId, byte[] _msg) {
            System.out.println("pong!");
            this.p2pMgr.send(_id, PING_MSG);
        }
    }

    @Ignore
    @Test
    public void testPingPong() throws InterruptedException {
        Map.Entry<P2pMgr, P2pMgr> pair = newTwoNodeSetup();

        P2pMgr connector = pair.getKey();
        List<Handler> handlers = Arrays.asList(new PingHandler(connector), new PongHandler(connector));
        connector.register(handlers);

        P2pMgr receiver = pair.getValue();

        handlers = Arrays.asList(new PingHandler(receiver), new PongHandler(receiver));
        receiver.register(handlers);
        receiver.run();
//        receiver.register(Collections.singletonList());
        Thread.sleep(1000L);
        connector.run();

        // wait for the two parties to connect
        Thread.sleep(5000L);

        // send initial message
        //connector.send(receiver., PING_MSG);

        Thread.sleep(10000L);
    }
}
