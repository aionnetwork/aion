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
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgApi;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RPC server implementation, based on josn rpc 2.0 spec: http://www.jsonrpc.org/specification
 *
 * Limitations: only handles positional parameters (no support for by-name parameters)
 *
 * @author chris lin, ali sharif
 */
public final class HttpServer
{
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private static RpcMsg process(final IRpc.Method _method, final JSONArray _params) throws Exception
    {
        if (LOG.isDebugEnabled())
            LOG.debug("<request mth=[{}] params={}>", _method.name(), _params.toString());

        RpcMsg response;

        if (_method == null)
            return new RpcMsg(null, RpcError.METHOD_NOT_FOUND);

        switch (_method) {

        /* -------------------------------------------------------------------------
        * web3
        */
            case web3_clientVersion: {
                response = api.web3_clientVersion();
                break;

            }
            case web3_sha3: {
                response = api.web3_sha3(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * net
         */
            case net_version: {
                response = api.net_version();
                break;
            }
            case net_peerCount: {
                response = api.net_peerCount();
                break;
            }
            case net_listening: {
                response = api.net_listening();
                break;
            }
        /* -------------------------------------------------------------------------
         * eth
         */
            case eth_protocolVersion: {
                response = api.eth_protocolVersion();
                break;
            }
            case eth_syncing: {
                response = api.eth_syncing();
                break;
            }
            case eth_coinbase: {
                response = api.eth_coinbase();
                break;
            }
            case eth_mining: {
                response = api.eth_mining();
                break;
            }
            case eth_hashrate: {
                response = api.eth_hashrate();
                break;
            }
            case eth_submitHashrate: {
                response = api.eth_submitHashrate(_params);
                break;
            }
            case eth_gasPrice: {
                response = api.eth_gasPrice();
                break;
            }
            case personal_listAccounts:
            case eth_accounts: {
                response = api.eth_accounts();
                break;
            }
            case eth_blockNumber: {
                response = api.eth_blockNumber();
                break;
            }
            case eth_getBalance: {
                response = api.eth_getBalance(_params);
                break;
            }
            case eth_getStorageAt: {
                response = api.eth_getStorageAt(_params);
                break;
            }
            case eth_getTransactionCount: {
                response = api.eth_getTransactionCount(_params);
                break;
            }
            case eth_getBlockTransactionCountByHash: {
                response = api.eth_getBlockTransactionCountByHash(_params);
                break;
            }
            case eth_getBlockTransactionCountByNumber: {
                response = api.eth_getBlockTransactionCountByNumber(_params);
                break;
            }
            case eth_getCode: {
                response = api.eth_getCode(_params);
                break;
            }
            case eth_sign: {
                response = api.eth_sign(_params);
                break;
            }
            case eth_sendTransaction: {
                response = api.eth_sendTransaction(_params);
                break;
            }
            case eth_sendRawTransaction: {
                response = api.eth_sendRawTransaction(_params);
                break;
            }
            case eth_call: {
                response = api.eth_call(_params);
                break;
            }
            case eth_estimateGas: {
                response = api.eth_estimateGas(_params);
                break;
            }
            case eth_getBlockByHash: {
                response = api.eth_getBlockByHash(_params);
                break;
            }
            case eth_getBlockByNumber: {
                response = api.eth_getBlockByNumber(_params);
                break;
            }
            case eth_getTransactionByHash: {
                response = api.eth_getTransactionByHash(_params);
                break;
            }
            case eth_getTransactionByBlockHashAndIndex: {
                response = api.eth_getTransactionByBlockHashAndIndex(_params);
                break;
            }
            case eth_getTransactionByBlockNumberAndIndex: {
                response = api.eth_getTransactionByBlockNumberAndIndex(_params);
                break;
            }
            case eth_getTransactionReceipt: {
                response = api.eth_getTransactionReceipt(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * compiler
         */
            case eth_getCompilers: {
                response = api.eth_getCompilers();
                break;
            }
            case eth_compileSolidity: {
                response = api.eth_compileSolidity(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * filters
         */
            case eth_newFilter: {
                response = api.eth_newFilter(_params);
                break;
            }
            case eth_newBlockFilter: {
                response = api.eth_newBlockFilter();
                break;
            }
            case eth_newPendingTransactionFilter: {
                response = api.eth_newPendingTransactionFilter();
                break;
            }
            case eth_uninstallFilter: {
                response = api.eth_uninstallFilter(_params);
                break;
            }
            case eth_getFilterLogs:
            case eth_getFilterChanges: {
                response = api.eth_getFilterChanges(_params);
                break;
            }
            case eth_getLogs: {
                response = api.eth_getLogs(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * personal
         */
            case personal_unlockAccount: {
                response = api.personal_unlockAccount(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * debug
         */
            case debug_getBlocksByNumber: {
                response = api.debug_getBlocksByNumber(_params);
                break;
            }
        /* -------------------------------------------------------------------------
         * stratum pool
         */
            case getinfo: {
                response = api.stratum_getinfo();
                break;
            }
            case getblocktemplate: {
                response = api.stratum_getblocktemplate();
                break;
            }
            case dumpprivkey: {
                response = api.stratum_dumpprivkey();
                break;
            }
            case validateaddress: {
                response = api.stratum_validateaddress(_params);
                break;
            }
            case getdifficulty: {
                response = api.stratum_getdifficulty();
                break;
            }
            case getmininginfo: {
                response = api.stratum_getmininginfo();
                break;
            }
            case submitblock: {
                response = api.stratum_submitblock(_params);
                break;
            }
            case getHeaderByBlockNumber: {
                response = api.stratum_getHeaderByBlockNumber(_params);
                break;
            }
            case ping: {
                response = new RpcMsg("pong");
                break;
            }
            default: {
                response = null;
                break;
            }
        }

        return response;
    }

    // -----------------------------------------------------------------------------------------------------------

    private static ApiWeb3Aion api;
    private Selector selector;
    private ServerSocketChannel tcpServer;
    private Thread tInbound;
    private volatile boolean start; // no need to make it atomic boolean. volatile does the job
    private ExecutorService workers;

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


    public HttpServer(final String _ip, final int _port, final String _corsDomain){
        this.ip = _ip;
        this.port = _port;
        if (_corsDomain != null && _corsDomain.length() > 0) {
            this.corsDomain = _corsDomain;
        } else {
            this.corsDomain = null;
        }


        this.api = new ApiWeb3Aion(AionImpl.inst());

        // create a pool of 3 at first, expand to 6 if we can't keep up
        this.workers = new ThreadPoolExecutor(
                3,
                6,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new RpcThreadFactory()
        );

        this.start = false;
    }

    private void writeResponse(final SocketChannel sc, final String response) throws Exception {
        ByteBuffer data = ByteBuffer.wrap(response.getBytes(CHARSET));

        while (data.hasRemaining()) {
            sc.write(data);
        }
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

    private class TaskRespond implements Runnable {

        private SocketChannel sc;
        private byte[] readBytes;

        public TaskRespond(SocketChannel sc, byte[] readBytes) {
            this.sc = sc;
            this.readBytes = readBytes;
        }

        @Override
        public void run() {
            try {
                // for empty requests, just close the socket channel
                if (readBytes.length > 0) {
                    String msg = new String(readBytes, "UTF-8").trim();
                    String response = null;

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

                    writeResponse(sc, response);
                }
            } catch (Exception e) {
                LOG.debug("<rpc-worker - failed to process incoming request. closing socketchannel. msg: {}>", new String(readBytes).trim(), e);
            } finally {
                try {
                    System.out.println("sc.close();");
                    sc.close();
                } catch (IOException e) {
                    LOG.error("<rpc-worker - socketchannel failed to close [8]>", e);
                }
            }
        }
    }

    private class TaskInbound implements Runnable {
        @Override
        public void run() {
            while (start) {
                try {
                    int num;
                    try{
                        num = selector.select();
                    } catch (IOException e){
                        continue;
                    }

                    if(num == 0)
                        continue;

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey sk = keys.next();
                        keys.remove();

                        if(!sk.isValid())
                            continue;

                        if (sk.isAcceptable()) {
                            SocketChannel tcpChannel = tcpServer.accept();
                            tcpChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                            tcpChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                            tcpChannel.configureBlocking(false);
                            tcpChannel.register(selector, SelectionKey.OP_READ);
                        }

                        if (sk.isReadable()) {
                            try {
                                SocketChannel sc = (SocketChannel) sk.channel();
                                ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
                                while (sc.read(readBuffer) > 0) { }
                                // dispatch to worker here
                                // worker closes the socket after writing to it
                                workers.submit(new TaskRespond(sc, readBuffer.array()));
                            } catch (Exception e) {
                                closeSocket((SocketChannel) sk.channel());
                            }
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
    }

    /**
     * @param _sc SocketChannel
     */
    private void closeSocket(final SocketChannel _sc) {
        try {
            SelectionKey sk = _sc.keyFor(selector);
            _sc.close();
            if (sk != null)
                sk.cancel();
        } catch (IOException e) {
            LOG.debug("<rpc-server - error closing socket [10]>", e);
        }
    }

    /*

    private static ApiWeb3Aion api;
    private Selector selector;
    private ServerSocketChannel tcpServer;
    private Thread tInbound;
    private volatile boolean start; // no need to make it atomic boolean. volatile does the job
    private ExecutorService workers;

     */


     //
    public void shutdown() throws InterruptedException {
        start = false;

        // wakeup the selector to run through the event loop one more time before exiting
        // NOTE: ok to do this from some shutdown thread since sun's implementation of Selector is threadsafe
        selector.wakeup();

        // graceful(ish) shutdown of thread pool:
        // NOTE: ok to call workers.*() from some shutdown thread since sun's implementation of ExecutorService is threadsafe
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                if (!workers.awaitTermination(5, TimeUnit.SECONDS))
                    LOG.debug("<rpc-server - main event loop failed to shutdown [11]>");
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

    public void start() {
        InetSocketAddress address = new InetSocketAddress(this.ip, this.port);

        try{
            this.tcpServer = ServerSocketChannel.open();
            this.tcpServer.configureBlocking(false);
            this.tcpServer.socket().setReuseAddress(true);
            this.tcpServer.socket().bind(address);
        } catch (IOException e) {
            LOG.info("<rpc-server-bind-failed bind={}:{}>", ip, port);
            System.exit(1);
        }

        try {
            selector = Selector.open();
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            if (LOG.isDebugEnabled())
                LOG.debug("<rpc-server-start bind={}:{}>", ip, port);

            tInbound = new Thread(new TaskInbound(), "rpc-server");
            tInbound.setPriority(Thread.NORM_PRIORITY);
            this.start = true;

            tInbound.start();
        } catch (IOException ex) {
            LOG.error("<rpc-server io-exception. potentially server failed to start [11]>");
        }
    }
}
