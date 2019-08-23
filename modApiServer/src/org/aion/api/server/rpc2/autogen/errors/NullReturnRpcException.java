package org.aion.api.server.rpc2.autogen.errors;

public class NullReturnRpcException extends org.aion.api.RpcException {

    public NullReturnRpcException(String data) {
        super(10003, "Null return", data);
    }
}
