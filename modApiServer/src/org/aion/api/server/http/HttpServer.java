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

    private ByteBuffer readBuffer;

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

        // allocate a 128mb buffer
        readBuffer = ByteBuffer.allocate(1024 * 128);
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
        } catch (Exception e) {
            LOG.info("<rpc-server - failed to bind on {}:{}>", ip, port, e);

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

        this.readBuffer.clear();

        // Attempt to read off the channel
        int bytesRead;
        try {
            bytesRead = sc.read(this.readBuffer);
        } catch (IOException e) {
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            sk.cancel();
            sc.close();
            return;
        }

        if (bytesRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            sk.channel().close();
            sk.cancel();
            return;
        }

        byte[] request = new byte[bytesRead];
        System.arraycopy(this.readBuffer.array(), 0, request, 0, bytesRead);

        rpcProcessor.process(this, sc, request);
    }

    private void write(SelectionKey sk) throws IOException {
        SocketChannel sc = (SocketChannel) sk.channel();

        synchronized (this.pendingData) {
            List<ByteBuffer> queue = this.pendingData.get(sc);

            // Write until there's not more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.get(0);
                sc.write(buf);
                if (buf.remaining() > 0) {
                    // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Close out.
                this.pendingData.remove(sc);
                sc.close();
                sc.keyFor(this.selector).cancel();
            }
        }
    }

    public void send(SocketChannel socket, ByteBuffer data) {
        synchronized (this.pendingChanges) {
            // Indicate we want the interest ops set changed
            this.pendingChanges.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData) {
                List<ByteBuffer> queue = this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList<>();
                    this.pendingData.put(socket, queue);
                }
                queue.add(data);
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    public void run() {
        int failcount = 0;
        while (!Thread.currentThread().isInterrupted()) {
            if (failcount > THREAD_ACCEPTABLE_FAIL_COUNT) {
                LOG.info("<rpc-server - SERVER DIED due to unhandlable exception.");
                break;
            }
            try {
                // Process any pending changes
                synchronized (pendingChanges) {
                    Iterator changes = pendingChanges.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(selector);
                                key.interestOps(change.ops);
                        }
                    }
                    pendingChanges.clear();
                }

                int updatedKeysCount;
                try {
                    // wait for an event one of the registered channels
                    updatedKeysCount = selector.select();
                } catch (IOException e) {
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
                    } else if (sk.isWritable()) {
                        write(sk);
                    }
                }
            } catch (Exception e) {
                failcount++;
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