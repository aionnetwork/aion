package org.aion.api.server.rpc;

import com.google.common.base.Stopwatch;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.api.server.account.AccountManager;
import org.aion.api.server.rpc2.Rpc2Shim;
import org.aion.api.server.rpc3.Web3EntryPoint;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

public class RpcProcessor {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private final Web3EntryPoint web3EntryPoint;

    private RpcMethods apiHolder;

    private ExecutorService executor;
    private CompletionService<JSONObject> batchCallCompletionService;
    private final int SHUTDOWN_WAIT_SECONDS = 5;

    private final Rpc2Shim rpc2Shim;

    public RpcProcessor(
        final List<String> enabledGroups,
        final List<String> enabledMethods,
        final List<String> disabledMethods,
        final Rpc2Shim rpc2Shim,
        final AccountManager am,
        final Web3EntryPoint web3EntryPoint) {

        if (web3EntryPoint == null) {
            throw new NullPointerException("Web3EntryPoint is null");
        }

        if (enabledGroups == null) {
            throw new NullPointerException("RpcProcessor enabledGroups is null");
        }

        if (enabledMethods == null) {
            throw new NullPointerException("RpcProcessor enabledMethods is null");
        }

        if (disabledMethods == null) {
            throw new NullPointerException("RpcProcessor disabledMethods is null");
        }

        if (rpc2Shim == null) {
            throw new NullPointerException("RpcProcessor rpc2Shim is null");
        }

        if (am == null) {
            throw new NullPointerException("RpcProcessor accountManager is null");
        }

        this.apiHolder = new RpcMethods(enabledGroups, enabledMethods, disabledMethods, am);
        executor =
                Executors.newFixedThreadPool(
                        Math.min(Runtime.getRuntime().availableProcessors() * 2, 4));
        batchCallCompletionService = new ExecutorCompletionService<>(executor);
        this.rpc2Shim = rpc2Shim;
        this.web3EntryPoint = web3EntryPoint;
    }

    public String process(String _requestBody) {
        String response = composeRpcResponse(new RpcMsg(null, RpcError.INVALID_REQUEST).toString());

        try {
            String requestBody = _requestBody.trim();
            if (!StringUtils.isEmpty(requestBody)) {
                char firstChar = requestBody.charAt(0);
                if (firstChar == '{') response = handleSingle(requestBody);
                else if (firstChar == '[') response = handleBatch(requestBody);
            }
        } catch (Exception e) {
            LOG.debug("<rpc-server - failed to process rpc request body>", e);
        }

        return response;
    }

    private String composeRpcResponse(String _respBody) {
        String respBody;
        if (_respBody == null) {
            respBody = new RpcMsg(null, RpcError.INTERNAL_ERROR).toString();
        } else {
            respBody = _respBody;
        }

        return respBody;
    }

    private JSONObject processObject(JSONObject body) {
        try {
            String method;
            Object params;
            Object id = JSONObject.NULL;

            try {
                // not checking for 'jsonrpc' key == 2.0. can pass in anything
                method = body.getString("method");
                Object _id = body.opt("id");
                if (_id != null) // loosen the rpc spec to allow client to not send an id.
                id = _id;
                params = body.opt("params");
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
                if (LOG.isDebugEnabled() && params != null)
                    LOG.debug("<request mth=[{}] params={}>", method, params.toString());
                else LOG.debug("<request mth=[{}]>", method);

                // Delegating timing request to Guava's Stopwatch
                boolean shouldTime = LOG.isDebugEnabled();
                Stopwatch timer = null;
                if (shouldTime) timer = Stopwatch.createStarted();
                RpcMsg response = rpc.call(params);
                if (shouldTime) {
                    timer.stop();
                    LOG.debug(
                            "<request mth=[{}] rpc-process time: [{}]>", method, timer.toString());
                }

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
    private String handleBatch(String _reqBody) {
        try {
            JSONArray reqBodies;

            try {
                reqBodies = new JSONArray(_reqBody);
                if (reqBodies.length() < 1) throw new Exception();
            } catch (Exception e) {
                // rpc call Batch, invalid JSON
                // rpc call with an empty Array
                LOG.debug("<rpc-server - rpc call parse error [4]>", e);
                return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString());
            }

            // time batch completion
            boolean shouldTime = LOG.isDebugEnabled();
            Stopwatch timer = null;
            if (shouldTime) timer = Stopwatch.createStarted();

            for (int i = 0; i < reqBodies.length(); i++) {
                batchCallCompletionService.submit(new BatchCallTask(reqBodies.getJSONObject(i)));
            }

            JSONArray respBodies = new JSONArray();
            for (int i = 0; i < reqBodies.length(); i++) {
                respBodies.put(batchCallCompletionService.take().get());
            }

            if (shouldTime) {
                timer.stop();
                LOG.debug(
                        "<batch request for [{}] entities finished in [{}]>",
                        reqBodies.length(),
                        timer.toString());
            }

            String respBody = respBodies.toString();

            if (LOG.isTraceEnabled()) LOG.trace("<rpc-server response={}>", respBody);

            return composeRpcResponse(respBody);

        } catch (Exception e) {
            LOG.debug("<rpc-server - internal error [6]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.INTERNAL_ERROR).toString());
    }

    private String handleSingle(String _reqBody) {
        try {
            JSONObject obj = new JSONObject(_reqBody);

            // This is a hook to to help transition to the new RPC server (TODO AKI-XXX).
            // If the new RPC server supports this method, delegate processing to the new RPC server
            String method = obj.optString("method");

            if (method != null && web3EntryPoint.isExecutable(method)){
                return web3EntryPoint.executeSingle(_reqBody);
            } else if(method != null  && Rpc2Shim.supportsMethod(method)) {
                return rpc2Shim.process(_reqBody);
            }

            return composeRpcResponse(processObject(obj).toString());
        } catch (Exception e) {
            // rpc call with invalid JSON
            LOG.debug("<rpc-server - rpc call parse error [7]>", e);
        }

        return composeRpcResponse(new RpcMsg(null, RpcError.PARSE_ERROR).toString());
    }

    private class BatchCallTask implements Callable<JSONObject> {
        private JSONObject task;

        public BatchCallTask(JSONObject task) {
            this.task = task;
        }

        @Override
        public JSONObject call() {
            try {
                return processObject(task);
            } catch (Exception e) {
                LOG.debug("<rpc-server - processObject failed in batch request>", e);
                return new RpcMsg(null, RpcError.INVALID_REQUEST, "INVALID_REQUEST").toJson();
            }
        }
    }

    public void shutdown() {
        apiHolder.shutdown();

        executor.shutdown();
        try {
            executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        // don't care about interruption on termination
    }
}
