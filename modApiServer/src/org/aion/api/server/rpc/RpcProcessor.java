package org.aion.api.server.rpc;

import org.aion.api.server.http.HttpServer;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RpcProcessor {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private static final int WORKERS_CORE_POOL_SIZE = 1;
    private static final String CHARSET = "UTF-8";
    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html
    private static final String CR = "\r";
    private static final String LF = "\n";
    private static final String CRLF = "\r\n";
    private static final String OPTIONS_TEMPLATE =
            "HTTP/1.1 200 OK" + CRLF +
                    "Server: Aion(J) RPC" + CRLF +
                    "Vary: Origin" + CRLF +
                    "Access-Control-Allow-Headers: *" + CRLF +
                    "Access-Control-Allow-Methods: POST, GET" + CRLF +
                    // use maximum max-age supported by popular browsers
                    // https://chromium.googlesource.com/chromium/blink/+/master/Source/core/loader/CrossOriginPreflightResultCache.cpp#40
                    "Access-Control-Max-Age: 600" + CRLF +
                    "Content-Length: 0" + CRLF +
                    "Content-Type: text/plain";
    private static final String POST_TEMPLATE =
            "HTTP/1.1 200 OK" + CRLF +
                    "Server: Aion(J) RPC" + CRLF +
                    "Vary: Origin" + CRLF +
                    "Content-Type: application/json";

    RpcMethods apiHolder;

    private ExecutorService workers;

    private List<String> allowedOrigins;
    private boolean allowedOriginsAll;
    private boolean corsEnabled;

    public RpcProcessor(List<String> allowedOrigins, List<String> enabled, int tpoolMaxSize) {

        this.corsEnabled = false;
        this.allowedOriginsAll = false;
        this.allowedOrigins = allowedOrigins;

        if (allowedOrigins == null ||
                allowedOrigins.size() == 0 ||
                (allowedOrigins.size() == 1 && (allowedOrigins.get(0).equalsIgnoreCase("null") || allowedOrigins.get(0).equals("") || allowedOrigins.get(0).equalsIgnoreCase("false")))) {
            this.allowedOrigins = null;
        }
        else {
            this.corsEnabled = true;
            for(String origin : allowedOrigins) {
                if (origin.equals("*")) {
                    this.allowedOriginsAll = true;
                    this.allowedOrigins = null;
                    break;
                }
            }
        }
        apiHolder = new RpcMethods(enabled);

        // protect user from over-allocating resources to api.
        // rationale: a sophisticated user would recompile the api-server with appropriate threading restrictions
        int fixedPoolSize = Math.min(Runtime.getRuntime().availableProcessors()-1, tpoolMaxSize);
        if (fixedPoolSize < 1) {
            fixedPoolSize = 1;
        }
        // create fixed thread pool of size defined by user
        this.workers = new ThreadPoolExecutor(
                fixedPoolSize,
                fixedPoolSize,
                5,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(100),
                new RpcThreadFactory()
        );
    }

    // checks if a given origin is allowed to perform cross-domain requests on endpoint
    boolean isOriginAllowed(String origin) {

        if (allowedOriginsAll == true) return true;
        if (allowedOrigins == null || origin == null || origin == "") return false;

        for (String allowedOrigin : allowedOrigins) {
            // purposefully do a weak origin check here
            // TODO: do a real origin check, supporting regex-defined origin
            if (origin.contains(allowedOrigin)) return true;
        }

        return false;
    }

    public void process(HttpServer server, SocketChannel sc, byte[] request) throws Exception {
        try {
            workers.submit(new RpcWorker(server, sc, request));
        } catch (Exception e) {
            LOG.error("<rpc-server - failed to submit task to thread pool. likely server under heavy load>", e);

            String response = composeRpcResponse(new RpcMsg(null, RpcError.SERVER_OVERLOAD).toString(), null);
            ByteBuffer data = ByteBuffer.wrap(response.getBytes(CHARSET));
            server.send(sc, data);
        }
    }

    private String composeRpcResponse(String _respBody, String _reqHeader) {
        String respBody;
        if (_respBody == null) {
            respBody = new RpcMsg(null, RpcError.INTERNAL_ERROR).toString();
        } else {
            respBody = _respBody;
        }

        int bodyLength = respBody.getBytes().length;
        String respHeader = POST_TEMPLATE;
        respHeader += CRLF + "Content-Length: " + bodyLength;

        // bother with parsing the origin header, only if cors is enabled
        if (corsEnabled) {
            if (_reqHeader == null) {
                respHeader += CRLF + "Access-Control-Allow-Origin: *";
            } else {
                String origin = parseOriginHeader(_reqHeader);
                if (isOriginAllowed(origin)) {
                    respHeader += CRLF + "Access-Control-Allow-Origin: " + origin;
                }
            }
        }

        if (bodyLength > 0)
            return (respHeader + CRLF + CRLF + respBody);
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

            RpcMethods.RpcMethod rpc = apiHolder.get(method);
            if (rpc == null) {
                LOG.debug("rpc-server - invalid method: {} [1]", method);
                return new RpcMsg(null, RpcError.METHOD_NOT_FOUND).setId(id).toJson();
            }

            try {
                LOG.debug("<request mth=[{}] params={}>", method, params.toString());
                RpcMsg response = rpc.call(params);
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

    private String parseOriginHeader(String msg) {
        // parse the origin header
        // make provision for buggy clients by splitting only on LF and ignoring leading CR
        // https://stackoverflow.com/questions/5757290/http-header-line-break-style
        String[] frags = msg.split(LF);
        String origin = null;
        for (String frag : frags) {
            if (frag.startsWith("Origin: ")) {
                origin = frag.replace("Origin: ", "").replace(CR, "");
                break;
            }
        }

        return origin;
    }

    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/OPTIONS
    // https://www.w3.org/TR/cors/#cors-api-specifiation-request
    private String handleOptions(SocketChannel sc, String msg) throws Exception {
        String respHeader = OPTIONS_TEMPLATE;

        // bother with parsing the origin header, only if cors is enabled
        if (corsEnabled) {
            String origin = parseOriginHeader(msg);
            if (isOriginAllowed(origin)) {
                respHeader += CRLF + "Access-Control-Allow-Origin: " + origin;
            }
        }

        return respHeader;
    }

    // implementing http://www.jsonrpc.org/specification#batch
    private String handleBatch(String _reqHeader, String _reqBody) throws Exception {
        try {
            JSONArray reqBodies;
            try {
                reqBodies = new JSONArray(_reqBody);
                if (reqBodies.length() < 1) throw new Exception();
            } catch (Exception e) {
                // rpc call Batch, invalid JSON
                // rpc call with an empty Array
                LOG.debug("<rpc-server - rpc call parse error [4]>", e);
                return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString(), _reqHeader);
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

            return composeRpcResponse(respBody, _reqHeader);

        } catch (Exception e) {
            LOG.debug("<rpc-server - internal error [6]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString(), _reqHeader);
    }

    private String handleSingle(String _reqHeader, String _reqBody) throws Exception{
        try {
            JSONObject obj = new JSONObject(_reqBody);
            return composeRpcResponse(processObject(obj).toString(), _reqHeader);
        } catch (Exception e) {
            // rpc call with invalid JSON
            LOG.debug("<rpc-server - rpc call parse error [7]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString(), _reqHeader);
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

                    // cors-preflight or options query
                    if (msg.startsWith("OPTIONS")) {
                        response = handleOptions(sc, msg);
                    } else {
                        String[] msgFrags = msg.split(CRLF+CRLF);

                        // make provision for buggy clients by splitting only on LF and ignoring leading CR
                        // https://stackoverflow.com/questions/5757290/http-header-line-break-style
                        if (msgFrags.length == 1) {
                            msg.split(LF+LF);
                        }

                        if (msgFrags.length == 2) {
                            String requestHeader = msgFrags[0];
                            String requestBody = msgFrags[1];
                            char firstChar = requestBody.charAt(0);
                            if (firstChar == '{')
                                response = handleSingle(requestHeader, requestBody);
                            else if (firstChar == '[')
                                response = handleBatch(requestHeader, requestBody);
                        }
                    }

                    if (response == null) {
                        response = composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString(), null);
                    }

                    try {
                        data = ByteBuffer.wrap(response.getBytes(CHARSET));
                    } catch (Exception e) {
                        LOG.debug("<rpc-worker - failed to convert response to bytearray [17]>", e);
                    }
                }
            } catch (Throwable e) {
                LOG.debug("<rpc-worker - failed to process incoming request>", e);
                try {
                    data = ByteBuffer.wrap(composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString(), null).getBytes(CHARSET));
                } catch (Exception f) {
                    LOG.debug("<rpc-worker - failed to convert response to bytearray [18]>", f);
                }
            } finally {
                // in any case, respond with empty byte array, even if it's empty, since most http client implementations
                // treat empty responses as 5xx errors anyway
                server.send(sc, data);
            }
        }
    }

    public void shutdown() {
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

        apiHolder.shutdown();
    }
}
