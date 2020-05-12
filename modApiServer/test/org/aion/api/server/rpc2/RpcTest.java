package org.aion.api.server.rpc2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;
import org.aion.api.server.AvmTestConfig;
import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.base.AionTransaction;
import org.aion.zero.impl.types.Block;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionBlockchain;
import org.aion.zero.impl.core.ImportResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RpcTest {

    private Rpc api;
    private AionImpl impl;

    @Before
    public void setup() {
        AvmTestConfig.supportOnlyAvmVersion1();

        impl = AionImpl.instForTest();
        impl.aionHub.enableUnityFork(2);
        api = new RpcImpl(impl);
    }

    @After
    public void tearDown() {
        impl.aionHub.disableUnityFork();
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testGetSeed() {
        IAionBlockchain chain = impl.aionHub.getBlockchain();
        Block block = chain.getBestBlock();
        List<AionTransaction> txs = Collections.emptyList();

        // expand chain to reach fork point
        block = chain.createNewMiningBlock(block, txs, false);
        ImportResult result = chain.tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        block = chain.createNewMiningBlock(block, txs, false);
        result = chain.tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);

        byte[] seed = api.getseed();

        assertNotNull(seed);
        assertArrayEquals(seed, new byte[64]);
    }
}
