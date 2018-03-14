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
import org.aion.api.server.types.*;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.mcf.config.CfgApi;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import org.aion.equihash.Solution;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aion.base.util.ByteUtil.toHexString;
import static org.aion.base.util.ByteUtil.hexStringToBytes;

/**
 * @author chris rpc server TODO: refactor as pooled response writing
 */
public final class HttpServer {

    /**
     * Basics
     */
    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.API.name());
    private static AtomicBoolean start = new AtomicBoolean(false);
    private static CfgApi cfg = CfgAion.inst().getApi();

    private static final String CHARSET = "UTF-8";
    private static final String CF = "\r\n";
    private static final String RES_OPTIONS_TEMPLATE = "HTTP/1.1 200 OK\n" + "Server: Aion\n"
            + "Access-Control-Allow-Headers: Content-Type\n" + "Access-Control-Allow-Origin: [ALLOW_ORIGIN]\n"
            + "Access-Control-Allow-Methods: POST, OPTIONS\n" + "Content-Length: 0\n" + "Content-Type: text/plain";

    private static final boolean allowCors = true;

    /**
     * References
     */
    private static ApiWeb3Aion api = new ApiWeb3Aion(AionImpl.inst());
    private static IP2pMgr p2pMgr;
    private static Selector selector;

    /**
     * Mining params
     */
    // TODO: Verify currentMining will follow kernel block generation schedule.
    // private static AtomicReference<AionBlock> currentMining;
    // TODO: Verify if need to use a concurrent map; locking may allow for use
    // of a simple map
    private static HashMap<String, AionBlock> templateMap;
    private static ReadWriteLock templateMapLock;

    private static JSONObject processResult(final long _id, final Object _result) {
        JSONObject json = new JSONObject();
        json.put("jsonrpc", "2.0");
        json.put("id", _id);
        json.put("result", _result);
        return json;
    }

    private static JSONObject process(final IRpc.Method _method, final long _id, final Object _params)
            throws Exception {
        if (log.isDebugEnabled())
            log.debug("<request mth=[{}] id={} params={}>", _method.name(), _id, _params.toString());
        JSONObject jsonObj;
        JSONArray params = (JSONArray) _params;
        AionBlock bestBlock;

        // TODO All of the eth_* methods need to be renamed to aion standard
        switch (_method) {

        /*
         * web3
         */
        case eth_accounts:
            return processResult(_id, new JSONArray(api.getAccounts()));

        case eth_blockNumber:
            return processResult(_id, api.getBestBlock().getNumber());

        case eth_coinbase:
            return processResult(_id, api.getCoinbase());

        case eth_compileSolidity:
            @SuppressWarnings("unchecked")
            Map<String, CompiledContr> compiled = api.contract_compileSolidity(params.get(0) + "");
            jsonObj = new JSONObject();
            for (String key : compiled.keySet()) {
                CompiledContr cc = compiled.get(key);
                jsonObj.put(key, cc.toJSON());
            }
            return processResult(_id, jsonObj);

        case personal_unlockAccount:
            String account = (String) params.get(0);
            String password = (String) params.get(1);
            int duration = new BigInteger(params.get(2).equals(null) ? "300" : params.get(2) + "").intValue();
            return processResult(_id, api.unlockAccount(account, password, duration));

        case eth_getBlockByNumber: {
            String number = (String) params.get(0);
            boolean fullTransactions = Boolean.parseBoolean(params.get(1) + "");

            if (number == null) {
                log.debug("eth_getBlockByNumber: invalid input");
                return processResult(_id, null);
            }
            return processResult(_id, api.eth_getBlock(number, fullTransactions));
        }

        case eth_getBlockByHash: {
            String hashString = (String) params.get(0);
                boolean fullTransactions = Boolean.parseBoolean(params.get(1) + "");
            if (hashString == null) {
                log.debug("eth_getBlockByHash: invalid input");
                return processResult(_id, null);
            }
            return processResult(_id, api.eth_getBlockByHash(hashString, fullTransactions));
        }

        case eth_getBalance:
            String address = params.get(0) + "";
            return processResult(_id, TypeConverter.toJsonHex(api.getBalance(address)));
        /*
        // rationale for not supporting this: does not make sense in the context of aion's getWork for minig.
        // see functions under 'stratum pool' descriptor in IRpc.java for currenly-supported stratum interactions
        case eth_getWork:
            // Header without nonce and solution , pool needs add new nonce
            return processResult(_id, toHexString(HashUtil.h256(api.getBestBlock().getHeader().getHeaderBytes(true))));
        */
        case eth_syncing:
            SyncInfo syncInfo = api.getSync();
            if (!syncInfo.done) {
                JSONObject obj = new JSONObject();
                // create obj for when syncing is completed
                obj.put("startingBlock", new NumericalValue(syncInfo.chainStartingBlkNumber).toHexString());
                obj.put("currentBlock", new NumericalValue(syncInfo.chainBestBlkNumber).toHexString());
                obj.put("highestBlock", new NumericalValue(syncInfo.networkBestBlkNumber).toHexString());
                obj.put("importMax", new NumericalValue(syncInfo.blksImportMax).toHexString());
                return processResult(_id, obj);
            } else {
                // create obj for when syncing is ongoing
                return processResult(_id, false);
            }

        case eth_call:
            if (JSONArray.class.isInstance(_params)) {
                JSONObject paramsObj = ((JSONArray) _params).getJSONObject(0);
                ArgTxCall txParams = ArgTxCall.fromJSON(paramsObj);
                if (txParams != null) {
                    byte[] res = api.doCall(txParams);
                    return processResult(_id, TypeConverter.toJsonHex(res));
                }
            }
            return processResult(_id, "");

        case eth_estimateGas: {
            if (_params instanceof JSONArray) {
                JSONObject obj = ((JSONArray) _params).getJSONObject(0);
                ArgTxCall txParams = ArgTxCall.fromJSON(obj);
                if (txParams != null) {
                    NumericalValue estimatedGas = new NumericalValue(api.estimateGas(txParams));
                    return processResult(_id, estimatedGas.toHexString());
                }
            }
            return processResult(_id, "");
        }
        case eth_sendTransaction:
            JSONObject paramsObj = ((JSONArray) _params).getJSONObject(0);
            ArgTxCall txParams = ArgTxCall.fromJSON(paramsObj);
            if (txParams != null) {
                byte[] res = api.sendTransaction(txParams);
                return processResult(_id, TypeConverter.toJsonHex(res));
            } else
                return processResult(_id, "");

        case eth_sendRawTransaction: {
            String rawHexString = (String) params.get(0);
            if (rawHexString == null) {
                log.debug("eth_sendRawTransaction invalid input");
                return processResult(_id, null);
            }

            byte[] rawTransaction;
            try {
                rawTransaction = ByteUtil.hexStringToBytes(rawHexString);
            } catch (Exception e) {
                log.debug("eth_sendRawTransaction invalid hex");
                return processResult(_id, null);
            }
            byte[] transactionHash = api.sendTransaction(rawTransaction);
            return processResult(_id, TypeConverter.toJsonHex(transactionHash));
        }

        case eth_newBlockFilter:
            return processResult(_id, api.eth_newBlockFilter());

        case eth_newFilter:
            ArgFltr fltr = new ArgFltr();

            /*
             * deal with events for one contract first
             */
            if (params.length() == 1) {
                JSONObject json = params.getJSONObject(0);
                fltr.address = Address.wrap(json.optString("address", ""));
                fltr.topics = json.optString("topics", "");
                return processResult(_id, api.eth_newFilter(fltr));
            } else
                return processResult(_id, "");

        case eth_getFilterLogs:
        case eth_getFilterChanges:
            return processResult(_id, api.eth_getFilterChanges(params.get(0) + ""));

        case eth_getTransactionReceipt:
            jsonObj = new JSONObject();
            TxRecpt txR = api.eth_getTransactionReceipt((String) params.get(0));
            if (txR != null) {
                jsonObj.put("transactionHash", txR.transactionHash);
                jsonObj.put("transactionIndex", new NumericalValue(txR.transactionIndex).toHexString());
                jsonObj.put("blockHash", txR.blockHash);
                jsonObj.put("blockNumber", new NumericalValue(txR.blockNumber).toHexString());
                jsonObj.put("cumulativeGasUsed", new NumericalValue(txR.cumulativeNrgUsed).toHexString());
                jsonObj.put("cumulativeNrgUsed", new NumericalValue(txR.cumulativeNrgUsed).toHexString());
                jsonObj.put("gasUsed", new NumericalValue(txR.nrgUsed).toHexString());
                jsonObj.put("nrgUsed", new NumericalValue(txR.nrgUsed).toHexString());
                jsonObj.put("contractAddress", txR.contractAddress);
                jsonObj.put("from", txR.from);
                jsonObj.put("to", txR.to);
                jsonObj.put("logsBloom", txR.logsBloom);
                jsonObj.put("status", txR.successful ? "0x1" : "0x0");

                JSONArray logArray = new JSONArray();
                for (int i = 0; i < txR.logs.length; i++) {
                    JSONObject obj = new JSONObject();
                    obj.put("address", txR.logs[i].address);
                    obj.put("data", txR.txData);
                    obj.put("blockNumber", new NumericalValue(txR.blockNumber).toHexString());
                    obj.put("transactionIndex", new NumericalValue(txR.transactionIndex).toHexString());
                    obj.put("logIndex", new NumericalValue(i).toHexString());

                    String[] topics = txR.logs[i].topics;
                    JSONArray topicArray = new JSONArray();
                    for (int j = 0; j < topics.length; j++) {
                        topicArray.put(j, topics[j]);
                    }
                    obj.put("topics", topicArray);
                    logArray.put(i, obj);
                }
                jsonObj.put("logs", logArray);
            }
            return processResult(_id, jsonObj);

        case eth_getTransactionByHash: {
            Tx transaction = api.eth_getTransactionByHash((String) params.get(0));
            JSONObject obj = new JSONObject();

            if (transaction != null) {
                obj.put("hash", transaction.transactionHash);
                obj.put("nonce", transaction.nonce.toHexString());
                obj.put("blockHash", transaction.blockHash);
                obj.put("blockNumber", transaction.blockNumber.toHexString());
                obj.put("transactionIndex", transaction.transactionIndex.toHexString());
                obj.put("from", transaction.from);
                obj.put("to", transaction.to);
                obj.put("value", transaction.value.toHexString());
                obj.put("gas", transaction.gas.toHexString());
                obj.put("gasPrice", transaction.gasPrice.toHexString());
                obj.put("nrg", transaction.gas.toHexString());
                obj.put("nrgPrice", transaction.gasPrice.toHexString());
                obj.put("input", transaction.input);
            }
            return processResult(_id, obj);
        }
        case eth_getTransactionCount: {
            String addr = (String) params.get(0);
            String requestedState = (String) params.get(1);

            NumericalValue reqNumber = requestedState == null || requestedState.equals("latest") ? null
                    : new NumericalValue(requestedState);
            NumericalValue nonce = api.eth_getTransactionCount(new Address(addr), reqNumber);
            return processResult(_id, nonce.toHexString());
        }
        case eth_getCode:
            return processResult(_id, api.eth_getCode(params.get(0) + ""));

        case eth_uninstallFilter:
            return processResult(_id, api.eth_uninstallFilter(params.get(0) + ""));

        /*
         * net
         */
        case net_listening:
            return processResult(_id, true);

        case net_peerCount:
            return processResult(_id, api.peerCount());

        /*
         * debug
         */
        case debug_getBlocksByNumber:
            String number = params.get(0) + "";
            boolean fullTransactions = Boolean.parseBoolean(params.get(1) + "");

            if (number == null) {
                log.debug("debug_getBlockInfoByHeight: invalid input");
                return processResult(_id, null);
            }

            JSONArray response = api.debug_getBlocksByNumber(number, fullTransactions);
            return processResult(_id, response);

        /*
         * stratum pool
         */
        case getinfo:
            jsonObj = new JSONObject();
            jsonObj.put("balance", 0);
            jsonObj.put("blocks", 0);
            jsonObj.put("connections", p2pMgr.getActiveNodes().size());
            jsonObj.put("proxy", "");
            jsonObj.put("generate", true);
            jsonObj.put("genproclimit", 100);
            jsonObj.put("difficulty", 0);
            return processResult(_id, jsonObj);

        case getblocktemplate:

            // TODO: Change this to a synchronized map implementation mapping
            // block hashes to the block. Allow multiple block templates at same
            // height.
            templateMapLock.writeLock().lock();

            // Assign to bestBlock to avoid multiple calls to .get()
            bestBlock = api.getBlockTemplate();

            // Check first entry in the map; if its height is higher a sync may
            // have switch branches, abandon current work to start on new branch
            if (!templateMap.keySet().isEmpty()) {
                if (templateMap.get(templateMap.keySet().iterator().next()).getNumber() < bestBlock.getNumber()) {
                    // Found a higher block, clear any remaining cached entries
                    // and start on new height
                    templateMap.clear();
                }
            }

            // Add template to map
            templateMap.put(toHexString(bestBlock.getHeader().getStaticHash()), bestBlock);

            jsonObj = new JSONObject();
            jsonObj.put("previousblockhash", toHexString(bestBlock.getParentHash()));
            jsonObj.put("height", bestBlock.getNumber());
            jsonObj.put("target", toHexString(BigInteger.valueOf(2).pow(256)
                    .divide(new BigInteger(bestBlock.getHeader().getDifficulty())).toByteArray())); // TODO: Pool eventually calculates itself
            jsonObj.put("transactions", new JSONArray()); // TODO: ? Might not

            // Add AION block header parameters to getblocktemplate
            jsonObj.putOpt("blockHeader", bestBlock.getHeader().toJSON());

            JSONObject coinbaseaux = new JSONObject();
            coinbaseaux.put("flags", "062f503253482f");
            jsonObj.put("coinbaseaux", coinbaseaux);

            jsonObj.put("headerHash", toHexString(bestBlock.getHeader().getStaticHash()));

            // TODO: Maybe move this further up
            templateMapLock.writeLock().unlock();

            return processResult(_id, jsonObj);

        case dumpprivkey:
            return processResult(_id, "");

        case validateaddress:
            /*
             * "isvalid" : true|false, (boolean) If the address is valid or not.
             * If not, this is the only property returned. "address" :
             * "address", (string) The bitcoin address validated "scriptPubKey"
             * : "hex", (string) The hex encoded scriptPubKey generated by the
             * address "ismine" : true|false, (boolean) If the address is yours
             * or not "iswatchonly" : true|false, (boolean) If the address is
             * watchonly "isscript" : true|false, (boolean) If the key is a
             * script "pubkey" : "publickeyhex", (string) The hex value of the
             * raw public key "iscompressed" : true|false, (boolean) If the
             * address is compressed "account" : "account" (string) DEPRECATED.
             * The account associated with the address, "" is the default
             * account "timestamp" : timestamp, (number, optional) The creation
             * time of the key if available in seconds since epoch (Jan 1 1970
             * GMT) "hdkeypath" : "keypath" (string, optional) The HD keypath if
             * the key is HD and available "hdmasterkeyid" : "<hash160>"
             * (string, optional) The Hash160 of the HD master pubkey
             */

            jsonObj = new JSONObject();
            jsonObj.put("isvalid", true);
            jsonObj.put("address", params.get(0));
            jsonObj.put("scriptPubKey", "hex");
            jsonObj.put("ismine", true);
            jsonObj.put("iswatchonly", true);
            jsonObj.put("isscript", true);
            jsonObj.put("timestamp", 0);
            jsonObj.put("hdkeypath", "");
            jsonObj.put("hdmasterkeyid", new byte[0]); // new byte[160]
            return processResult(_id, jsonObj);

        case getdifficulty:
            return processResult(_id, 0x4000);
        // TODO: This needs to be refactored to return valid data
        case getmininginfo:
            bestBlock = api.getBestBlock();
            jsonObj = new JSONObject();
            jsonObj.put("blocks", bestBlock.getNumber());
            jsonObj.put("currentblocksize", 0);
            jsonObj.put("currentblocktx", bestBlock.getTransactionsList().size());
            jsonObj.put("difficulty", 3368767.14053294);
            jsonObj.put("errors", "");
            jsonObj.put("genproclimit", -1);
            jsonObj.put("hashespersec", 0);
            jsonObj.put("pooledtx", 0);
            jsonObj.put("testnet", false);
            return processResult(_id, jsonObj);

        case submitblock:

            jsonObj = new JSONObject();

            if (params.length() > 0) {

                templateMapLock.writeLock().lock();

                String nce = (String) params.get(0);
                String soln = (String) params.get(1);
                String hdrHash = (String) params.get(2);
                String nTime = (String) params.get(3);

                bestBlock = templateMap.get(hdrHash);

                boolean successfulSubmit = false;
                // TODO Clean up this section once decided on event vs direct
                // call
                if (bestBlock != null) {
                    successfulSubmit = api
                            .submitBlock(new Solution(bestBlock, hexStringToBytes(nce), hexStringToBytes(soln), Long.parseLong(nTime, 16)));
                }

                if (successfulSubmit) {
                    // Found a solution for this height and successfully
                    // submitted, clear all entries for next height
                    log.info("block sealed via api <num={}, hash={}, diff={}, tx={}>", bestBlock.getNumber(),
                            bestBlock.getShortHash(),
                            bestBlock.getHeader().getDifficultyBI().toString(), bestBlock.getTransactionsList().size());
                    templateMap.clear();
                }

                templateMapLock.writeLock().unlock();

                // TODO: Simplified response for now, need to provide better
                // feedback to caller in next update
                JSONObject json = new JSONObject();
                json.put("jsonrpc", "2.0");
                json.put("id", _id);
                json.put("result", true);

                return json;

            } else {
                // Expected on pool initialization
                jsonObj.put("message", "success");
                jsonObj.put("code", -1);
                return processResult(_id, jsonObj);
            }

        case getHeaderByBlockNumber:
            jsonObj = new JSONObject();

            if (params.length() == 1) {

                int bn;

                try {
                    bn = (Integer) params.get(0);

                    AionBlock block = api.getBlockRaw(bn);

                    if (block != null) {
                        A0BlockHeader header = block.getHeader();

                        // Add code (0) to show successful
                        jsonObj.put("code", 0);

                        // Place remaining pieces into the JSON object
                        jsonObj.put("nonce", toHexString(header.getNonce()));
                        jsonObj.put("solution", toHexString(header.getSolution()));
                        jsonObj.put("headerHash", toHexString(HashUtil.h256(header.getHeaderBytes(false))));

                        // Add AION block header parameters to getblocktemplate
                        jsonObj.putOpt("blockHeader", header.toJSON());

                    } else {

                        jsonObj.put("message", "Fail - Unable to find block" + params.get(0));
                        jsonObj.put("code", -2);
                    }

                } catch (ClassCastException e) {
                    jsonObj.put("message", params.get(0) + " must be an integer value");
                    jsonObj.put("code", -3);
                }

            } else {
                jsonObj.put("message", "Missing block number");
                jsonObj.put("code", -1);
            }

            return processResult(_id, jsonObj);
        case ping:
            return processResult(_id, "pong");
        default:
            return processResult(_id, "");
        }
    }

    private static void handleOptions(final SocketChannel _sc, final String _msg) throws IOException {

        String[] frags = _msg.split("\n");
        String reqOrigin = "";
        for (String frag : frags) {
            if (frag.startsWith("Origin: "))
                reqOrigin = frag.replace("Origin: ", "");
        }

        ByteBuffer buf = ByteBuffer
                .wrap(RES_OPTIONS_TEMPLATE.replace("[ALLOW_ORIGIN]", allowCors ? "*" : reqOrigin).getBytes(CHARSET));

        try {
            while (buf.hasRemaining()) {
                _sc.write(buf);
            }
        } catch (IOException e) {
            log.error("<api options-write-io-exception>");
        } finally {
            _sc.close();
        }
    }

    public static void start(final IP2pMgr _p2pMgr) {
        p2pMgr = _p2pMgr;

        templateMap = new HashMap<>();
        templateMapLock = new ReentrantReadWriteLock();

        if (!start.get()) {
            start.set(true);
            if (cfg.getRpc().getActive()) {
                try {
                    selector = Selector.open();
                    if (cfg.getRpc().getActive()) {
                        String ip = cfg.getRpc().getIp();
                        int port = cfg.getRpc().getPort();

                        InetSocketAddress address = new InetSocketAddress(ip, port);
                        ServerSocketChannel tcpServer = ServerSocketChannel.open();
                        tcpServer.configureBlocking(false);
                        tcpServer.socket().setReuseAddress(true);
                        tcpServer.socket().bind(address);
                        tcpServer.register(selector, SelectionKey.OP_ACCEPT);

                        if (log.isDebugEnabled())
                            log.debug("<rpc action=start bind={}:{}>", ip, port);

                        Thread processInbound = new Thread(() -> {
                            // while (start.get()) {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    if (selector.selectNow() > 0) {
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
                                                if (sk.isReadable()) {
                                                    SocketChannel sc = (SocketChannel) sk.channel();

                                                    /*
                                                     * TODO: complete
                                                     * Content-Length read
                                                     */
                                                    ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
                                                    while (sc.read(readBuffer) > 0) {
                                                        sc.read(readBuffer);
                                                    }
                                                    try {
                                                        byte[] readBytes = readBuffer.array();
                                                        if (readBytes.length > 0) {

                                                            String msg = new String(readBytes, "UTF-8").trim();

                                                            if (msg.startsWith("OPTIONS"))
                                                                handleOptions(sc, msg);

                                                            String[] msgFrags = msg.split(CF);
                                                            int docBreaker = 0;
                                                            int len = msgFrags.length;

                                                            for (int i = 0; i < len; i++) {
                                                                if (msgFrags[i].isEmpty())
                                                                    docBreaker = i;
                                                            }
                                                            if (docBreaker + 2 == len) {
                                                                String requestBody = msgFrags[docBreaker + 1];
                                                                /*
                                                                 * TODO: clear
                                                                 * this part
                                                                 * index 1
                                                                 */
                                                                char firstChar = requestBody.charAt(0);

                                                                if (firstChar == '{') {
                                                                    /*
                                                                     * single
                                                                     * call
                                                                     */
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
                                                                        JSONObject resJson = process(method,
                                                                                Long.parseLong(idObj.toString()),
                                                                                paramsObj);
                                                                        String responseBody = resJson == null ? ""
                                                                                : resJson.toString();
                                                                        String responseHeader = "HTTP/1.1 200 OK\n"
                                                                                + "Content-Length: "
                                                                                + responseBody.getBytes().length + "\n"
                                                                                + "Content-Type: application/json\n"
                                                                                + "Access-Control-Allow-Origin: *\n\n";

                                                                        if (log.isDebugEnabled())
                                                                            log.debug("<response mths=[{}] result={}>",
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
                                                                    /*
                                                                     * batch
                                                                     * calls
                                                                     */
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
                                                                        if (idObj != null && method != null)
                                                                            responseBodies.put(process(method,
                                                                                    Long.parseLong(idObj.toString()),
                                                                                    paramsObj));
                                                                    }
                                                                    String responseBody = responseBodies.toString();

                                                                    if (log.isDebugEnabled())
                                                                        log.debug("<response mths=[{}] result={}>",
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
                                                            }
                                                        } else
                                                            sc.close();
                                                    } catch (Exception ex) {
                                                        sc.close();
                                                    }
                                                }
                                            } catch (CancelledKeyException | IOException ex) {
                                                ex.printStackTrace();
                                                sk.channel().close();
                                                sk.cancel();
                                            }
                                        }
                                    }
                                    Thread.sleep(1);
                                } catch (IOException | InterruptedException e1) {
                                    if (log.isDebugEnabled())
                                        log.debug("<rpc-io-exception>");
                                }
                            }
                        });
                        processInbound.setName("rpc-server");
                        processInbound.start();
                    }

                } catch (IOException ex) {
                    log.error("<api io-exception>");
                }
            }
        }
    }
}
