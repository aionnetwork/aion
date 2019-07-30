package org.aion.api.server.rpc2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RpcTest {

    private Rpc api;
    private AionImpl impl;

    @Before
    public void setup() {
        impl = AionImpl.inst();
        api = new RpcImpl(impl);
        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @After
    public void tearDown() {
        LongLivedAvm.destroy();
    }

    @Test
    public void testGetSeed() {

        byte[] seed = api.getseed();

        assertNotNull(seed);
        assertArrayEquals(seed, new byte[64]);
    }


}
