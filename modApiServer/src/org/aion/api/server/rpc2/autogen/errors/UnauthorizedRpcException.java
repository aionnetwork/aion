package org.aion.api.server.rpc2.autogen.errors;

public class UnauthorizedRpcException extends org.aion.api.RpcException {

    public UnauthorizedRpcException(String data) {
        super(10001, "Unauthorized", data);
    }
}
