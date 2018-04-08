/*******************************************************************************
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
 *
 ******************************************************************************/
package org.aion.api.server.http;

import org.aion.api.server.rpc.RpcProcessor;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * RPC server implementation, based on josn rpc 2.0 spec: http://www.jsonrpc.org/specification
 *
 * Limitations: only handles positional parameters (no support for by-name parameters)
 *
 * @author chris lin, ali sharif
 */

// http://rox-xmlrpc.sourceforge.net/niotut/index.html
// http://www.onjava.com/pub/a/onjava/2004/09/01/nio.html?page=3
// http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf
public class HttpServer implements Runnable {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    // maximum number of times the main thread can exception out without dying
    // rationale: if there is an infinite loop, this will
    public static final int THREAD_ACCEPTABLE_FAIL_COUNT = 1000;

    private Selector selector;
    private ServerSocketChannel tcpServer;

    private List<ChangeRequest> pendingChanges;
    private Map<SocketChannel, List<ByteBuffer>> pendingData;

    private RpcProcessor rpcProcessor;
    private Thread server;

    private String ip;
    private int port;

    public static class ChangeRequest {
        public static final int CHANGEOPS = 1;

        public SocketChannel socket;
        public int type;
        public int ops;

        public ChangeRequest(SocketChannel socket, int type, int ops) {
            this.socket = socket;
            this.type = type;
            this.ops = ops;
        }
    }

    public HttpServer(String _ip, int _port, List<String> _corsDomain, List<String> enabled, int tpoolMaxSize) {
        rpcProcessor = new RpcProcessor(_corsDomain, enabled, tpoolMaxSize);

        pendingChanges = new LinkedList<>();
        pendingData = new HashMap<>();

        ip = _ip;
        port = _port;
    }

    public void start() {
        try {
            InetSocketAddress address = new InetSocketAddress(ip, port);
            this.selector = Selector.open();

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(address);
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);
        } catch (Throwable e) {
            LOG.info("<rpc-server - failed to bind on {}:{}>", ip, port, e);
            System.exit(1);
        }

        server = new Thread(this::run, "rpc-server");
        server.setPriority(Thread.NORM_PRIORITY);
        server.start();

        LOG.info("<rpc-server - started on {}:{}>", ip, port);
    }

    private void accept(SelectionKey sk) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();

        SocketChannel tcpChannel = serverSocketChannel.accept();
        tcpChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        tcpChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey sk) throws Exception {
        SocketChannel sc = (SocketChannel) sk.channel();

        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
            while (sc.read(readBuffer) > 0) { }
            // dispatch to worker here
            // worker closes the socket after writing to it
            rpcProcessor.process(this, sc, readBuffer.array());
        } catch (Exception e) {
            LOG.debug("<rpc-server - failed to read data from socket.>", e);
            sk.cancel();
            sc.close();
        }

    }

    public void send(SocketChannel socket, ByteBuffer data) {
        try {
            while (data.hasRemaining()) {
                socket.write(data);
            }
        } catch (Exception e) {
            LOG.debug("failed to write response", e);
        } finally {
            try {
                socket.close();
                SelectionKey sk = socket.keyFor(this.selector);
                if (sk != null)
                    sk.cancel();
                else
                    System.out.println("<rpc-server - selector key is null>");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int updatedKeysCount = 0;
                try {
                    // wait for an event one of the registered channels
                    updatedKeysCount = selector.select();
                } catch (IOException e) {
                    LOG.debug("<rpc-server - selector.select() failed somehow>");
                    continue;
                }

                // nothing to do here. go back and wait on selector.select()
                if (updatedKeysCount == 0)
                    continue;

                // iterate over the set of keys for which events are available
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey sk = keys.next();
                    keys.remove();

                    if (!sk.isValid())
                        continue;

                    if (sk.isAcceptable()) {
                        accept(sk);
                    } else if (sk.isReadable()) {
                        read(sk);
                    }
                }
            } catch (Throwable e) {
                LOG.debug("<rpc-server - main event loop uncaught error [9]>", e);
            }
        }
        LOG.debug("<rpc-sever - main event loop returning [11]>");
    }

    // usually called by the kernel's shutdown thread
    public void shutdown() {
        server.interrupt();

        // wakeup the selector to run through the event loop one more time before exiting
        // NOTE: ok to do this from some shutdown thread since sun's implementation of Selector.wakeup() is threadsafe
        selector.wakeup();

        // shutdown workerthreads
        rpcProcessor.shutdown();

        // wait for the server thread to shutdown and report if it failed
        try {
            server.join(3000L);
            if (server.isAlive())
                LOG.debug("<rpc-server - graceful shutdown incomplete>");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        LOG.debug("<rpc-server - graceful shutdown complete>");
    }
}