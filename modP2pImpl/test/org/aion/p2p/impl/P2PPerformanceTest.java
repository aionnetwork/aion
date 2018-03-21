package org.aion.p2p.impl;

import org.aion.p2p.Handler;
import org.aion.p2p.Header;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class P2PPerformanceTest {

    public Map.Entry<P2pMgr, P2pMgr> newTwoNodeSetup() {
        String ip = "127.0.0.1";

        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        int port1 = 30303;
        int port2 = 30304;
        return null;
    }

    private static class PingHandler extends Handler {
        public PingHandler(short _ver, byte _ctrl, byte _act) {
            super((short) 0, (byte) 64, (byte) 0);
        }

        @Override
        public Header getHeader() {
            return super.getHeader();
        }

        @Override
        public void receive(int _id, String _displayId, byte[] _msg) {

        }
    }

    @Test
    public void testPingPong() throws InterruptedException {
        Map.Entry<P2pMgr, P2pMgr> pair = newTwoNodeSetup();
        P2pMgr connector = pair.getKey();
        P2pMgr receiver = pair.getValue();

        receiver.run();
//        receiver.register(Collections.singletonList());

        Thread.sleep(1000L);

        connector.run();
    }
}
