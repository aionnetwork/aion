package org.aion.p2p.impl;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtilities {
    private TestUtilities() {}

    /**
     * Tries to return a free port thats currently not used
     *
     * @return
     */
    public static int getFreePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            socket.close();

            return port;
        } catch (IOException e) {
            throw new IllegalStateException("could not find free TCP/IP port");
        }
    }

    static String formatAddr(String id, String ip, int port) {
        return id + "@" + ip + ":" + port;
    }
}
