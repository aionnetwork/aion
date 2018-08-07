package org.aion.api.server.rpc;

import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.List;

public class RpcProcessor {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    RpcMethods apiHolder;

    public RpcProcessor(List<String> enabled) {
        this.apiHolder = new RpcMethods(enabled);
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

                /**
                 * Note about using System.nanoTime():
                 * It's slower (~5x) than using System.currentTimeMillis() based on emperical tests across machines
                 * and operating systems. But since this only runs in debug mode, it's probably OK?
                 */
                boolean shouldTime = LOG.isDebugEnabled();
                long t0 = 0L;
                if (shouldTime) t0 = System.nanoTime();
                RpcMsg response = rpc.call(params);
                if (shouldTime) {
                    long t1 = System.nanoTime();
                    LOG.debug("<request mth=[{}] rpc-process time: {}ms>", method, (t1 - t0) / 10e6f);
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

    public void shutdown() {
        apiHolder.shutdown();
    }
}
