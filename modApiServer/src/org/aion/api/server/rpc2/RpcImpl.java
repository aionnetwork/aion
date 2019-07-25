package org.aion.api.server.rpc2;

import org.aion.api.server.rpc2.autogen.Rpc;

public class RpcImpl implements Rpc {

    /* Test impls only to check RpcProcessor2 works properly */

    @Override
    public byte[] getseed() {
        return new byte[] { 0x0, 0x1, 0x2, 0x3, 0x4,
                            0x5, 0x6, 0x7, 0x8, 0x9,
                            0xa};
    }

    @Override
    public byte[] submitseed(byte[] var0, byte[] var1) {
        return var1;
    }
}
