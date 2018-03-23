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

import org.aion.api.server.IRpc;
import org.aion.api.server.IRpc.Method;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.blockchain.AionImpl;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
public class HttpServer implements Runnable
{
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private Selector selector;
    //private ServerSocketChannel tcpServer;
    private Thread tInbound;
    private volatile boolean start; // no need to make it atomic boolean. volatile does the job
    private ExecutorService workers;
    private ByteBuffer readBuffer;

    private List<ChangeRequest> pendingChanges = new LinkedList();
    private Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap();

    // configuration parameters
    private final String ip;
    private final int port;
    private final String corsDomain;

    private static final String CHARSET = "UTF-8";
    private static final String CF = "\r\n";
    private static final String OPTIONS_TEMPLATE =
                                        "HTTP/1.1 200 OK\n" +
                                        "Server: Aion(J) RPC\n" +
                                        "Access-Control-Allow-Headers: Content-Type\n" +
                                        "Access-Control-Allow-Methods: POST, OPTIONS\n" +
                                        "Content-Length: 0\n" +
                                        "Content-Type: text/plain";
    private static final String POST_TEMPLATE =
                                        "HTTP/1.1 200 OK\n" +
                                        "Server: Aion(J) RPC\n" +
                                        "Content-Type: application/json";


    public static class ChangeRequest {
        public static final int REGISTER = 1;
        public static final int CHANGEOPS = 2;

        public SocketChannel socket;
        public int type;
        public int ops;

        public ChangeRequest(SocketChannel socket, int type, int ops) {
            this.socket = socket;
            this.type = type;
            this.ops = ops;
        }
    }

    public void send(SocketChannel socket, ByteBuffer data) {
        synchronized (this.pendingChanges) {
            // Indicate we want the interest ops set changed
            this.pendingChanges.add(new ChangeRequest(socket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData) {
                List queue = (List) this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socket, queue);
                }
                queue.add(data);
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    public HttpServer(final String _ip, final int _port, final String _corsDomain) throws IOException {
        this.ip = _ip;
        this.port = _port;
        if (_corsDomain != null && _corsDomain.length() > 0) {
            this.corsDomain = _corsDomain;
        } else {
            this.corsDomain = null;
        }

        // allocate a 1GB big buffer - overkill
        this.readBuffer = ByteBuffer.allocate(1024 * 1024);

        // create a pool of 2 at first, expand to 6 if we can't keep up
        this.workers = new ThreadPoolExecutor(
                Math.min(Runtime.getRuntime().availableProcessors(), 2),
                Math.min(Runtime.getRuntime().availableProcessors(), 6),
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new RpcThreadFactory()
        );

        this.selector = this.initSelector();

        this.start = false;

        LOG.info("<rpc-server - started on {}:{}>", ip, port);
    }

    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS
    // https://www.w3.org/TR/cors/#cors-api-specifiation-request
    private String handleOptions(final SocketChannel sc, final String msg) throws Exception {
        // parse the origin header
        String[] frags = msg.split("\n");
        String origin = "";
        for (String frag : frags) {
            if (frag.startsWith("Origin: ")) {
                origin = frag.replace("Origin: ", "");
                break;
            }
        }

        String respHeader = OPTIONS_TEMPLATE;
        if (corsDomain != null) {
            respHeader += "\nAccess-Control-Allow-Origin: " + corsDomain;
        }

        return respHeader;
    }

    private String composeRpcResponse(String _respBody) {
        String respBody;
        if (_respBody == null) {
            respBody = new RpcMsg(null, RpcError.INTERNAL_ERROR).toString();
        } else {
            respBody = _respBody;
        }

        int bodyLength = respBody.getBytes().length;
        String respHeader = POST_TEMPLATE;
        respHeader += "\nContent-Length: " + bodyLength;
        if (corsDomain != null) {
            respHeader += "\nAccess-Control-Allow-Origin: " + corsDomain;
        }

        if (bodyLength > 0)
            return (respHeader + "\n\n" + respBody);
        else
            return (respHeader);
    }

    private JSONObject processObject(JSONObject body) {
        try {
            String method;
            JSONArray params;
            Object id = JSONObject.NULL;

            try {
                // not checking for 'jsonrpc' key == 2.0. can pass in anything
                method = body.getString("method");
                id = body.get("id");
                params = body.getJSONArray("params");
            } catch (Exception e) {
                LOG.debug("<rpc-server - invalid rpc request [0]>", e);
                return new RpcMsg(null, RpcError.INVALID_REQUEST).toJson();
            }

            Method rpc = null;
            try {
                rpc = Method.valueOf(method);
            } catch (Exception e) {
                LOG.debug("rpc-server - invalid method [1]", e);
                return new RpcMsg(null, RpcError.METHOD_NOT_FOUND).setId(id).toJson();
            }

            try {
                RpcMsg response = process(rpc, params);
                return response.setId(id).toJson();
            } catch (Exception e) {
                LOG.debug("<rpc-server - internal error [2]>", e);
                return new RpcMsg(null, RpcError.INTERNAL_ERROR).setId(id).toJson();
            }
        } catch (Exception e) {
            LOG.debug("<rpc-server - internal error [3]>", e);
        }

        return new RpcMsg(null, RpcError.INTERNAL_ERROR).toJson();
    }

    // implementing http://www.jsonrpc.org/specification#batch
    private String handleBatch(String msg) throws Exception {
        try {
            JSONArray reqBodies;
            try {
                reqBodies = new JSONArray(msg);
                if (reqBodies.length() < 1) throw new Exception();
            } catch (Exception e) {
                // rpc call Batch, invalid JSON
                // rpc call with an empty Array
                LOG.debug("<rpc-server - rpc call parse error [4]>", e);
                return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString());
            }

            JSONArray respBodies = new JSONArray();

            for (int i = 0, n = reqBodies.length(); i < n; i++) {
                try {
                    JSONObject body = reqBodies.getJSONObject(i);
                    respBodies.put(processObject(body));
                } catch (Exception e) {
                    LOG.debug("<rpc-server - invalid rpc request [5]>", e);
                    respBodies.put(new RpcMsg(null, RpcError.INVALID_REQUEST).toJson());
                }
            }

            String respBody = respBodies.toString();

            if (LOG.isDebugEnabled())
                LOG.debug("<rpc-server response={}>", respBody);

            return composeRpcResponse(respBody);

        } catch (Exception e) {
            LOG.debug("<rpc-server - internal error [6]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString());
    }

    private String handleSingle(String msg) throws Exception{
        JSONArray reqBodies;
        try {
            JSONObject obj = new JSONObject(msg);
            return composeRpcResponse(processObject(obj).toString());
        } catch (Exception e) {
            // rpc call with invalid JSON
            LOG.debug("<rpc-server - rpc call parse error [7]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString());
    }

    private class RpcWorker implements Runnable {

        private HttpServer server;
        private SocketChannel sc;
        private byte[] readBytes;

        public RpcWorker(HttpServer server, SocketChannel sc, byte[] readBytes) {
            this.server = server;
            this.sc = sc;
            this.readBytes = readBytes;
        }

        @Override
        public void run() {

            ByteBuffer data = ByteBuffer.wrap(new byte[0]);
            try {
                String response = null;

                // for empty requests, just close the socket channel
                if (readBytes.length > 0) {
                    String msg = new String(readBytes, "UTF-8").trim();

                    // cors preflight or options query
                    if (msg.startsWith("OPTIONS")) {
                        response = handleOptions(sc, msg);
                    } else {
                        String[] msgFrags = msg.split(CF);
                        int docBreaker = 0;
                        int len = msgFrags.length;

                        for (int i = 0; i < len; i++) {
                            if (msgFrags[i].isEmpty())
                                docBreaker = i;
                        }
                        if (docBreaker + 2 == len) {
                            String requestBody = msgFrags[docBreaker + 1];
                            char firstChar = requestBody.charAt(0);
                            if (firstChar == '{')
                                response = handleSingle(requestBody);
                            else if (firstChar == '[')
                                response = handleBatch(requestBody);
                        }
                    }

                    if (response == null) {
                        response = composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString());
                    }

                    try {
                        data = ByteBuffer.wrap(response.getBytes(CHARSET));
                    } catch (Exception e) {
                        LOG.debug("<rpc-worker - failed to convert response to bytearray [17]>", e);
                    }
                }
            } catch (Exception e) {
                LOG.debug("<rpc-worker - failed to process incoming request. closing socketchannel. msg: {}>", new String(readBytes).trim(), e);
            } finally {
                server.send(sc, data);
            }
        }
    }

    private void accept(SelectionKey sk) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();

        SocketChannel tcpChannel = serverSocketChannel.accept();
        tcpChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        tcpChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_READ);
    }

    private void read(SelectionKey sk) throws IOException {
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

        workers.submit(new RpcWorker(this, sc, readBuffer.array()));
    }

    public void run() {
        while (start) {
            try {
                // Process any pending changes
                synchronized (this.pendingChanges) {
                    Iterator changes = this.pendingChanges.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.pendingChanges.clear();
                }

                int updatedKeysCount;
                try{
                    // wait for an event one of the registered channels
                    updatedKeysCount = selector.select();
                } catch (IOException e){
                    continue;
                }

                // nothing to do here. go back and wait on selector.select()
                if(updatedKeysCount == 0)
                    continue;

                // iterate over the set of keys for which events are available
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey sk = keys.next();
                    keys.remove();

                    if(!sk.isValid())
                        continue;

                    if (sk.isAcceptable()) {
                        this.accept(sk);
                    } else if (sk.isReadable()) {
                        this.read(sk);
                    } else if (sk.isWritable()) {
                        this.write(sk);
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    LOG.debug("<rpc-server - main event loop interrupted [10]>");
                    break;
                }

            } catch (Exception e) {
                LOG.debug("<rpc-server - main event loop uncaught error [9]>", e);
            }
        }
        LOG.debug("<rpc-sever - main event loop returning [11]>");
    }

    private void write(SelectionKey sk) throws IOException {
        SocketChannel sc = (SocketChannel) sk.channel();

        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(sc);

            // Write until there's not more data ...
            while (!queue.isEmpty()) {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
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
                //key.interestOps(SelectionKey.OP_READ);
                sc.close();
                sc.keyFor(this.selector).cancel();
            }
        }
    }
     //
    public void shutdown() {
        start = false;

        // wakeup the selector to run through the event loop one more time before exiting
        // NOTE: ok to do this from some shutdown thread since sun's implementation of Selector.wakeup() is threadsafe
        selector.wakeup();

        // graceful(ish) shutdown of thread pool
        // NOTE: ok to call workers.*() from some shutdown thread since sun's implementation of ExecutorService is threadsafe
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                if (!workers.awaitTermination(5, TimeUnit.SECONDS))
                    LOG.debug("<rpc-server - main event loop failed to shutdown [13]>");
            }
        } catch (InterruptedException ie) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // graceful(ish) shutdown of webserver thread
        try {
            tInbound.join(3000L);
            if (tInbound.isAlive()) {
                tInbound.interrupt();

            }
        } catch (InterruptedException ie) {
            tInbound.interrupt();
            Thread.currentThread().interrupt();
        }

        LOG.debug("<rpc-server - graceful shutdown complete [-1]>");
    }

    private Selector initSelector() throws IOException {

        InetSocketAddress address = new InetSocketAddress(this.ip, this.port);

        ServerSocketChannel tcpServer = ServerSocketChannel.open();
        tcpServer.configureBlocking(false);
        tcpServer.socket().setReuseAddress(true);
        tcpServer.socket().bind(address);

        Selector selector = Selector.open();
        tcpServer.register(selector, SelectionKey.OP_ACCEPT);

        return selector;
    }
}
