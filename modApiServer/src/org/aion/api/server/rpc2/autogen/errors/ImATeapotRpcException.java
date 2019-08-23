package org.aion.api.server.rpc2.autogen.errors;

public class ImATeapotRpcException extends org.aion.api.RpcException {

    public ImATeapotRpcException(String data) {
        super(10418, "I'm a teapot", data);
    }
}
