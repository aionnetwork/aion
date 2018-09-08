package org.aion.api.server.rpc;

import com.google.common.base.Stopwatch;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RpcProcessor {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    RpcMethods apiHolder;
    ExecutorService executor;
    CompletionService<JSONObject> batchCallCompletionService;
    private final int MAX_SHUTDOWN_WAIT = 5;

    public RpcProcessor(final List<String> enabledGroups, final List<String> enabledMethods, final List<String> disabledMethods) {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        batchCallCompletionService = new ExecutorCompletionService(executor);
        this.apiHolder = new RpcMethods(enabledGroups, enabledMethods, disabledMethods);
    }

    public String process(String _requestBody) {
        String response = composeRpcResponse(new RpcMsg(null, RpcError.INVALID_REQUEST).toString());

        try {
            String requestBody = _requestBody.trim();
            if (!StringUtils.isEmpty(requestBody)) {
                char firstChar = requestBody.charAt(0);
                if (firstChar == '{')
                    response = handleSingle(requestBody);
                else if (firstChar == '[')
                    response = handleBatch(requestBody);
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
                else
                    LOG.debug("<request mth=[{}]>", method);

                // Delegating timing request to Guava's Stopwatch
                boolean shouldTime = LOG.isDebugEnabled();
                Stopwatch timer = null;
                if (shouldTime) timer = Stopwatch.createStarted();
                RpcMsg response = rpc.call(params);
                if (shouldTime) {
                    timer.stop();
                    LOG.debug("<request mth=[{}] rpc-process time: [{}]>", method, timer.toString());
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

            JSONArray respBodies = new JSONArray();

            for(int i = 0; i < reqBodies.length(); i++) {
                batchCallCompletionService.submit(new BatchCallTask(reqBodies.getJSONObject(i)));
            }

            for(int i = 0; i < reqBodies.length(); i++) {
                respBodies.put(batchCallCompletionService.take().get());
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

    private String handleSingle(String _reqBody) {
        try {
            JSONObject obj = new JSONObject(_reqBody);
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
                return new RpcMsg(null, RpcError.INVALID_REQUEST, "INVALID_REQUEST").toJson();
            }
        }
    }

    public void shutdown() {
        apiHolder.shutdown();

        executor.shutdown();
        try {
            executor.awaitTermination(MAX_SHUTDOWN_WAIT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {

        }

    }
}
