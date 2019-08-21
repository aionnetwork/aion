package org.aion.api.server.rpc2;

import org.aion.api.RpcException;
import org.aion.api.serialization.JsonRpcError;
import org.aion.api.serialization.JsonRpcRequest;
import org.aion.api.serialization.JsonRpcResponse;
import org.aion.api.serialization.RequestDeserializer;
import org.aion.api.serialization.ResponseSerializer;
import org.aion.api.server.rpc2.autogen.TemplatedSerializer;

public abstract class AbstractRpcProcessor {
    private RequestDeserializer reqDeserializer;
    private ResponseSerializer respSerializer;

    public AbstractRpcProcessor() {
        reqDeserializer = new RequestDeserializer(new TemplatedSerializer());
        respSerializer = new ResponseSerializer();
    }

    public String process(String payload) {
        JsonRpcRequest req;
        try {
            req = reqDeserializer.deserialize(payload);
        } catch(RpcException rx) {
            JsonRpcError err = new JsonRpcError(rx,
                    reqDeserializer.idOfRequest(payload));
            return respSerializer.serializeError(err);
        } catch (Exception e) {
            JsonRpcError err = new JsonRpcError(
                    RpcException.internalError(
                            "Deserialization error: " +
                                    org.json.JSONObject.quote(e.toString())),
                    reqDeserializer.idOfRequest(payload)
            );
            return respSerializer.serializeError(err);
        }

        try {
            Object execResult = execute(req);
            JsonRpcResponse resp = new JsonRpcResponse(execResult, req.getId());
            return respSerializer.serialize(resp, req.getMethod());
        } catch(RpcException rx) {
            JsonRpcError err = new JsonRpcError(rx, req.getId());
            return respSerializer.serializeError(err);
        } catch(Exception ex) {
            JsonRpcError err = new JsonRpcError(
                    RpcException.internalError(org.json.JSONObject.quote(ex.toString())),
                    req.getId());
            return respSerializer.serializeError(err);
        }
    }

    protected abstract Object execute(JsonRpcRequest payload) throws RpcException;
}
