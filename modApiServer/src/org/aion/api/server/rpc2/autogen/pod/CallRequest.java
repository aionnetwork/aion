package org.aion.api.server.rpc2.autogen.pod;

public class CallRequest {
    private byte[] to;
    private java.math.BigInteger value;
    private byte[] data;

    public CallRequest(
        byte[] to,
        java.math.BigInteger value,
        byte[] data
    ) {
        this.to = to;
        this.value = value;
        this.data = data;
    }

    public byte[] getTo() {
        return this.to;
    }

    public java.math.BigInteger getValue() {
        return this.value;
    }

    public byte[] getData() {
        return this.data;
    }

}
