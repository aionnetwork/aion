package org.aion.p2p.impl;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.SocketChannel;

public class TestUtilities {
    private TestUtilities() {}

    /**
     * Tries to return a free port thats currently not used
     * @return
     */
    public static int getFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {}
            return port;
        } catch (IOException e) {

        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }
        throw new IllegalStateException("could not find tree TCP/IP port");
    }

    public static String formatAddr(String id, String ip, int port) {
        return id + "@" + ip + ":" + port;
    }
}
