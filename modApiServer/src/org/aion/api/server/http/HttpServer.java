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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris rpc server TODO: refactor as pooled response writing
 * TODO: make implementation pass all spec tests: https://github.com/ethereum/rpc-tests
 */
public final class HttpServer
{
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private static JSONObject process(final IRpc.Method _method, final long _id, final Object _params) throws Exception
    {
        if (LOG.isDebugEnabled())
            LOG.debug("<request mth=[{}] id={} params={}>", _method.name(), _id, _params.toString());

        JSONArray params = (JSONArray) _params;

        /* Rationale for pushing all the function implementations (even trivial ones) up to
         * ApiWeb3Aion is to keep all implementations in one place and separate concerns
         *
         * In these case statement, the only things that are enforced are:
         * 1. Optional vs mandatory fields (get() vs opt())
         * 2. Any type checking and casting (bool, string, object, etc.) as defined by api spec
         */

        switch (_method) {

        /* -------------------------------------------------------------------------
        * web3
        */
            case web3_clientVersion: {
                return processResult(_id, api.web3_clientVersion());
            }
            case web3_sha3: {
                String data = params.get(0) + "";
                return processResult(_id, api.web3_sha3(data));
            }
        /* -------------------------------------------------------------------------
         * net
         */
            case net_version: {
                return processResult(_id, api.net_version());
            }
            case net_peerCount: {
                return processResult(_id, api.net_peerCount());
            }
            // currently, p2p manager is always listening for peers and is active
            case net_listening: {
                return processResult(_id, api.net_listening());
            }
        /* -------------------------------------------------------------------------
         * eth
         */
            case eth_protocolVersion: {
                return processResult(_id, api.eth_protocolVersion());
            }
            case eth_syncing: {
                return processResult(_id, api.eth_syncing());
            }
            case eth_coinbase: {
                return processResult(_id, api.eth_coinbase());
            }
            case eth_mining: {
                return processResult(_id, api.eth_mining());
            }
            case eth_hashrate: {
                return processResult(_id, api.eth_hashrate());
            }
            case eth_submitHashrate: {
                String hashrate = params.get(0) + "";
                String clientId = params.get(1) + "";
                return processResult(_id, api.eth_submitHashrate(hashrate, clientId));
            }
            case eth_gasPrice: {
                return processResult(_id, api.eth_gasPrice());
            }
            case personal_listAccounts:
            case eth_accounts: {
                return processResult(_id, api.eth_accounts());
            }
            case eth_blockNumber: {
                return processResult(_id, api.eth_blockNumber());
            }
            case eth_getBalance: {
                String address = params.get(0) + "";
                Object bnOrId = params.opt(1);
                return processResult(_id, api.eth_getBalance(address, bnOrId));
            }
            case eth_getStorageAt: {
                String address = params.get(0) + "";
                String index = params.get(1) + "";
                Object bnOrId = params.opt(2);
                return processResult(_id, api.eth_getStorageAt(address, index, bnOrId));
            }
            case eth_getTransactionCount: {
                String address = params.get(0) + "";
                Object bnOrId = params.opt(1);
                return processResult(_id, api.eth_getTransactionCount(address, bnOrId));
            }
            case eth_getBlockTransactionCountByHash: {
                String hash = params.get(0) + "";
                return processResult(_id, api.eth_getBlockTransactionCountByHash(hash));
            }
            case eth_getBlockTransactionCountByNumber: {
                String bnOrId = params.get(0) + "";
                return processResult(_id, api.eth_getBlockTransactionCountByNumber(bnOrId));
            }
            case eth_getCode: {
                String address = params.get(0) + "";
                Object bnOrId = params.opt(1);
                return processResult(_id, api.eth_getCode(address, bnOrId));
            }
            case eth_sign: {
                String address = params.get(0) + "";
                String message = params.get(1) + "";
                return processResult(_id, api.eth_sign(address, message));
            }
            case eth_sendTransaction: {
                JSONObject tx = params.getJSONObject(0);
                return processResult(_id, api.eth_sendTransaction(tx));
            }
            case eth_sendRawTransaction: {
                return processResult(_id, api.eth_sendRawTransaction(params.get(0) + ""));
            }
            case eth_call: {
                JSONObject tx = params.getJSONObject(0);
                Object bnOrId = params.opt(1);
                return processResult(_id, api.eth_call(tx, bnOrId));
            }
            case eth_estimateGas: {
                JSONObject tx = params.getJSONObject(0);
                return processResult(_id, api.eth_estimateGas(tx));
            }
            case eth_getBlockByHash: {
                String hash = params.get(0) + "";
                boolean fullTx = params.optBoolean(1, false);
                return processResult(_id, api.eth_getBlockByHash(hash, fullTx));
            }
            case eth_getBlockByNumber: {
                String bnOrId = params.get(0) + "";
                boolean fullTx = params.optBoolean(1, false);
                return processResult(_id, api.eth_getBlockByNumber(bnOrId, fullTx));
            }
            case eth_getTransactionByHash: {
                String hash = params.get(0) + "";
                return processResult(_id, api.eth_getTransactionByHash(hash));
            }
            case eth_getTransactionByBlockHashAndIndex: {
                String hash = params.get(0) + "";
                String index = params.get(1) + "";
                return processResult(_id, api.eth_getTransactionByBlockHashAndIndex(hash, index));
            }
            case eth_getTransactionByBlockNumberAndIndex: {
                String bnOrId = params.get(0) + "";
                String index = params.get(1) + "";
                return processResult(_id, api.eth_getTransactionByBlockNumberAndIndex(bnOrId, index));
            }
            case eth_getTransactionReceipt: {
                String hash = params.get(0) + "";
                return processResult(_id, api.eth_getTransactionReceipt(hash));
            }
        /* -------------------------------------------------------------------------
         * compiler
         */
            case eth_getCompilers: {
                return processResult(_id, api.eth_getCompilers());
            }
            case eth_compileSolidity: {
                String contract = params.get(0) + "";
                return processResult(_id, api.eth_compileSolidity(contract));
            }
        /* -------------------------------------------------------------------------
         * filters
         */
            case eth_newFilter: {
                JSONObject filterObj = params.getJSONObject(0);
                return processResult(_id, api.eth_newFilter(filterObj));
            }
            case eth_newBlockFilter: {
                return processResult(_id, api.eth_newBlockFilter());
            }
            case eth_newPendingTransactionFilter: {
                return processResult(_id, api.eth_newPendingTransactionFilter());
            }
            case eth_uninstallFilter: {
                String id = params.get(0) + "";
                return processResult(_id, api.eth_uninstallFilter(id));
            }
            case eth_getFilterChanges: {
                String id = params.get(0) + "";
                return processResult(_id, api.eth_getFilterChanges(id));
            }
            case eth_getFilterLogs: {
                String id = params.get(0) + "";
                return processResult(_id, api.eth_getFilterLogs(id));
            }
            case eth_getLogs: {
                JSONObject filterObj = params.getJSONObject(0);
                return processResult(_id, api.eth_getLogs(filterObj));
            }
        /* -------------------------------------------------------------------------
         * personal
         */
            case personal_unlockAccount: {
                String account = params.get(0) + "";
                String password = params.get(1) + "";
                Object duration = params.opt(2);
                return processResult(_id, api.personal_unlockAccount(account, password, duration));
            }
        /* -------------------------------------------------------------------------
         * debug
         */
            case debug_getBlocksByNumber: {
                String number = params.get(0) + "";
                boolean fullTx = params.optBoolean(1, false);

                return processResult(_id, api.debug_getBlocksByNumber(number, fullTx));
            }
        /* -------------------------------------------------------------------------
         * stratum pool
         */
            case getinfo: {
                return processResult(_id, api.stratum_getinfo());
            }
            case getblocktemplate: {
                return processResult(_id, api.stratum_getblocktemplate());
            }
            case dumpprivkey: {
                return processResult(_id, api.stratum_dumpprivkey());
            }
            case validateaddress: {
                String address = params.get(0) + "";
                return processResult(_id, api.stratum_validateaddress(address));
            }
            case getdifficulty: {
                return processResult(_id, api.stratum_getdifficulty());
            }
            case getmininginfo: {
                return processResult(_id, api.stratum_getmininginfo());
            }
            case submitblock: {
                Object nce = params.opt(0);
                Object soln = params.opt(1);
                Object hdrHash = params.opt(2);
                Object ts = params.opt(3);
                return processResult(_id, api.stratum_submitblock(nce, soln, hdrHash, ts));
            }
            case getHeaderByBlockNumber: {
                Object blkNum = params.opt(0);
                return processResult(_id, api.stratum_getHeaderByBlockNumber(blkNum));
            }
            case ping: {
                return processResult(_id, "pong");
            }
            default: {
                return processResult(_id, null);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------


    private static CfgApi cfg = CfgAion.inst().getApi();

    private static final String CHARSET = "UTF-8";
    private static final String CF = "\r\n";
    private static final String RES_OPTIONS_TEMPLATE =
        "HTTP/1.1 200 OK\n" +
        "Server: Aion(J) Web3\n" +
        "Access-Control-Allow-Headers: Content-Type\n" +
        "Access-Control-Allow-Origin: [ALLOW_ORIGIN]\n" +
        "Access-Control-Allow-Methods: POST, OPTIONS\n" +
        "Content-Length: 0\n" +
        "Content-Type: text/plain";

    private static final boolean ALLOW_CORS = true;
    private static final String CORS_STRING = "*";

    private static ApiWeb3Aion api = new ApiWeb3Aion(AionImpl.inst());

    // TODO: overload this so we can pass error messages to the client
    // TODO: make this compliant with the JSON RPC 2.0 spec: http://www.jsonrpc.org/specification
    // optionally, support codes from Error Codes Improvement EIP:
    // https://github.com/ethereum/wiki/wiki/JSON-RPC-Error-Codes-Improvement-Proposal
    private static JSONObject processResult(final long _id, final Object _result) {
        JSONObject json = new JSONObject();
        json.put("jsonrpc", "2.0");
        json.put("id", _id);

        // according to the json-rpc spec (http://www.jsonrpc.org/specification):
        // error: This member is REQUIRED on error. This member MUST NOT exist if there was no error triggered during invocation.
        // result: This member is REQUIRED on success. This member MUST NOT exist if there was an error invoking the method.
        if (_result == null) { // call equals on the leaf type
            JSONObject error  = new JSONObject();
            error.put("code", -32600);
            error.put("message", "Invalid Request");

            json.put("error", error);
        } else {
            json.put("result", _result);
        }
        return json;
    }

    // https://www.html5rocks.com/en/tutorials/cors/
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS
    private static void handleOptions(final SocketChannel _sc, final String _msg) throws IOException {

        String[] frags = _msg.split("\n");
        String reqOrigin = "";
        for (String frag : frags) {
            if (frag.startsWith("Origin: "))
                reqOrigin = frag.replace("Origin: ", "");
        }

        ByteBuffer buf = ByteBuffer.wrap(RES_OPTIONS_TEMPLATE.replace("[ALLOW_ORIGIN]", ALLOW_CORS ? CORS_STRING : reqOrigin).getBytes(CHARSET));

        try {
            while (buf.hasRemaining()) {
                _sc.write(buf);
            }
        } catch (IOException e) {
            LOG.error("<api options-write-io-exception>");
        } finally {
            _sc.close();
        }
    }

    private static class ProcessInbound extends Thread
    {
        public void run() {

            while (!Thread.currentThread().isInterrupted())
            {
                try {
                    Thread.sleep(1);

                    if (selector.selectNow() <= 0)
                        continue;

                    Set<SelectionKey> sks = selector.selectedKeys();
                    Iterator<SelectionKey> it = sks.iterator();
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        it.remove();

                        try {
                            if (sk.isAcceptable()) {
                                SocketChannel tcpChannel = tcpServer.accept();
                                tcpChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                                tcpChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                                tcpChannel.configureBlocking(false);
                                tcpChannel.register(selector, SelectionKey.OP_READ);
                            }
                            if (!sk.isReadable()) continue;

                            SocketChannel sc = (SocketChannel) sk.channel();

                            // TODO: complete Content-Length read instead of 'allocating' 1GB everytime
                            ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
                            while (sc.read(readBuffer) > 0) {
                                sc.read(readBuffer);
                            }

                            try {
                                byte[] readBytes = readBuffer.array();
                                if (readBytes.length <= 0) {
                                    sc.close();
                                    continue;
                                }

                                String msg = new String(readBytes, CHARSET).trim();

                                if (msg.startsWith("OPTIONS"))
                                    handleOptions(sc, msg);

                                String[] msgFrags = msg.split(CF);
                                int docBreaker = 0;
                                int len = msgFrags.length;

                                for (int i = 0; i < len; i++) {
                                    if (msgFrags[i].isEmpty()) docBreaker = i;
                                }

                                if (docBreaker + 2 != len) {
                                    sc.close();
                                    continue;
                                }

                                String requestBody = msgFrags[docBreaker + 1];

                                char firstChar = requestBody.charAt(0);

                                if (firstChar == '{') {
                                    // single call
                                    JSONObject bodyObj = new JSONObject(requestBody);
                                    Object methodObj = bodyObj.get("method");
                                    Object idObj = bodyObj.get("id");
                                    Object paramsObj = bodyObj.getJSONArray("params");

                                    Method method = null;
                                    try {
                                        if (methodObj != null) {
                                            String methodStr = (String) methodObj;
                                            method = Method.valueOf(methodStr);
                                        } else
                                            sc.close();
                                    } catch (IllegalArgumentException ex) {
                                        sc.close();
                                    }

                                    if (idObj != null && method != null) {
                                        JSONObject resJson = null;
                                        try {
                                            resJson = process(method,
                                                    Long.parseLong(idObj.toString()),
                                                    paramsObj);
                                        } catch (Exception e) {
                                            LOG.debug("method {} threw exception.", methodObj+"", e);
                                            resJson = processResult(Long.parseLong(idObj.toString()), null);
                                        }
                                        String responseBody = resJson == null ? ""
                                                : resJson.toString();
                                        String responseHeader = "HTTP/1.1 200 OK\n"
                                                + "Content-Length: " + responseBody.getBytes().length + "\n"
                                                + "Content-Type: application/json\n"
                                                + "Access-Control-Allow-Origin: *\n\n";

                                        if (LOG.isDebugEnabled())
                                            LOG.debug("<response mths=[{}] result={}>",
                                                    method.toString(), responseBody);

                                        String response = responseHeader + responseBody;
                                        ByteBuffer resultBuffer = ByteBuffer
                                                .wrap((response).getBytes(CHARSET));
                                        while (resultBuffer.hasRemaining()) {
                                            try {
                                                sc.write(resultBuffer);
                                            } catch (IOException e) {
                                                break;
                                            }
                                        }
                                        sc.close();
                                    } else
                                        sc.close();
                                } else if (firstChar == '[') {

                                    // batch calls
                                    JSONArray requestBodies = new JSONArray(
                                            requestBody);
                                    JSONArray responseBodies = new JSONArray();
                                    List<String> methodStrs = new ArrayList<>();
                                    for (int i = 0, m = requestBodies
                                            .length(); i < m; i++) {
                                        JSONObject bodyObj = requestBodies
                                                .getJSONObject(i);
                                        Object methodObj = bodyObj.get("method");
                                        Object idObj = bodyObj.get("id");
                                        Object paramsObj = bodyObj
                                                .getJSONArray("params");

                                        Method method = null;
                                        try {
                                            if (methodObj != null) {
                                                String methodStr = (String) methodObj;
                                                method = Method.valueOf(methodStr);
                                                methodStrs.add(methodStr);
                                            } else
                                                sc.close();
                                        } catch (IllegalArgumentException ex) {
                                            sc.close();
                                        }
                                        if (idObj != null && method != null) {
                                            JSONObject resJson = null;
                                            try {
                                                resJson = process(method,
                                                        Long.parseLong(idObj.toString()),
                                                        paramsObj);
                                            } catch (Exception e) {
                                                LOG.debug("method {} threw exception.", methodObj+"", e);
                                                resJson = processResult(Long.parseLong(idObj.toString()), null);
                                            }
                                            responseBodies.put(resJson);
                                        }
                                    }
                                    String responseBody = responseBodies.toString();

                                    if (LOG.isDebugEnabled())
                                        LOG.debug("<response mths=[{}] result={}>",
                                                String.join(",", methodStrs),
                                                responseBody);
                                    String responseHeader = "HTTP/1.1 200 OK\n"
                                            + "Content-Length: "
                                            + responseBody.getBytes().length + "\n"
                                            + "Content-Type: application/json\n"
                                            + "Access-Control-Allow-Origin: *\n";
                                    ByteBuffer resultBuffer = ByteBuffer
                                            .wrap((responseHeader + "\n" + responseBody)
                                                    .getBytes(CHARSET));
                                    while (resultBuffer.hasRemaining()) {
                                        try {
                                            sc.write(resultBuffer);
                                        } catch (IOException e) {
                                            sc.close();
                                            break;
                                        }
                                    }
                                } else
                                    sc.close();


                            } catch (Exception ex) {
                                sc.close();
                            }
                        } catch (CancelledKeyException | IOException ex) {
                            ex.printStackTrace();
                            sk.channel().close();
                            sk.cancel();
                        }
                    }
                } catch (IOException | InterruptedException e1) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("<rpc-io-exception>");
                }
            }
        }
    }

    private static AtomicBoolean start = new AtomicBoolean(false);
    private static Selector selector;
    private static ServerSocketChannel tcpServer;

    public static void start() {

        if (start.get() == true)
            return;
        // shady way to create a singleton
        start.set(true);

        if (!cfg.getRpc().getActive())
            return;

        try {
            selector = Selector.open();

            String ip = cfg.getRpc().getIp();
            int port = cfg.getRpc().getPort();

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(new InetSocketAddress(ip, port));
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            LOG.debug("<rpc action=start bind={}:{}>", ip, port);

            ProcessInbound process = new ProcessInbound();
            process.setName("rpc-server");
            process.start();
        } catch (IOException ex) {
            LOG.error("<api io-exception>");
        }
    }
}
