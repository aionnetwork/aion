package org.aion.api.server.rpc2;

import java.io.IOException;
import org.aion.api.serialization.JsonRpcRequest;
import org.aion.api.serialization.JsonRpcResponse;
import org.aion.api.serialization.RequestDeserializer;
import org.aion.api.serialization.ResponseSerializer;
import org.aion.api.serialization.RpcTypeDeserializer;
import org.aion.api.server.rpc2.autogen.TemplatedSerializer;

public abstract class AbstractRpcProcessor {
    private RequestDeserializer reqDeserializer;
    private ResponseSerializer respSerializer;

    public AbstractRpcProcessor() {
        reqDeserializer = new RequestDeserializer(new TemplatedSerializer());
        respSerializer = new ResponseSerializer(null /*not needed yet*/);
    }

    public String process(String payload) throws Exception {
        JsonRpcRequest req = reqDeserializer.deserialize(payload);
        JsonRpcResponse resp = new JsonRpcResponse(execute(req), req.getId());
        return respSerializer.serialize(resp, req.getMethod());
    }

    protected abstract Object execute(JsonRpcRequest payload) throws Exception;


}
