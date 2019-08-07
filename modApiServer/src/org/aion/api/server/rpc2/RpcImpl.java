package org.aion.api.server.rpc2;

import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.util.bytes.ByteUtil;

public class RpcImpl implements Rpc {

    /* Test impls only to check RpcProcessor2 works properly */

    @Override
    public byte[] getseed() {
        return ByteUtil.hexStringToBytes("0xc0ffee000000000000000000000000000000000000000000000000000000cafec0ffee000000000000000000000000000000000000000000000000000000cafe");
    }

    @Override
    public byte[] submitseed(byte[] var0, byte[] var1) {
        return var1;
    }

    @Override
    public boolean submitsignature(byte[] var0, byte[] var1) {
        return false; //TODO
    }
}
