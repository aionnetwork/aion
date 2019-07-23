package org.aion.api.server.rpc2;

import java.util.List;
import org.json.JSONObject;

public class RpcProcessor2 {
    public static final List<String> SUPPORTED_METHOD_NAMES = List.of(
        "getseed", "submitseed", "submitwork"
    );

    public static boolean supportsMethod(String methodName) {
        return SUPPORTED_METHOD_NAMES.contains(methodName);
    }


    public static String process(JSONObject payload) {
        return "{\"result\": \"0.0\", \"id\": \"1\", \"jsonrpc\":\"2.0\"}";
    }
}
