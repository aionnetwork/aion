package org.aion.zero.impl.blockchain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Properties;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Test;

public class AionBlockchainImplTest {

    private StandaloneBlockchain blockchain;

    @After
    public void tearDown() {

        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork1.0", String.valueOf(Long.MAX_VALUE));
        cfg.getFork().setProperties(p);
        this.blockchain = null;
    }

    @Test
    public void testUnityMininmumNumber() {
        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork1.0", "1");
        cfg.getFork().setProperties(p);

        StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();
        this.blockchain = bundle.bc;

        assertNotNull(blockchain);
        //AKI-419 the minimum fork point is 2, therefore, the unity protocol will start from 3
        assertFalse(blockchain.forkUtility.isUnityForkActive(2));
        assertTrue(blockchain.forkUtility.isUnityForkActive(3));
    }

    @Test
    public void testUnityMininmumNumber2() {
        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork1.0", "2");
        cfg.getFork().setProperties(p);

        StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();
        this.blockchain = bundle.bc;

        assertNotNull(blockchain);
        assertFalse(blockchain.forkUtility.isUnityForkActive(2));
        assertTrue(blockchain.forkUtility.isUnityForkActive(3));
    }

    @Test
    /**
     * we only test the mining block template has been cleared. The method call in the AionBlockchainImpl
     * also proved the staking block template will be cleaned at the same time.
     */
    public void testBlockTemplateClearWhenChainBranched() {
        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork1.0", "4");
        cfg.getFork().setProperties(p);

        StandaloneBlockchain.Bundle bundle =
            new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();
        this.blockchain = bundle.bc;

        assertNotNull(blockchain);

        AionBlock block1a = blockchain.createBlockAndBlockTemplate(blockchain.genesis, new ArrayList<>(), false, blockchain.genesis.getTimestamp() + 10);
        Pair<ImportResult, AionBlockSummary> connectResult = blockchain.tryToConnectAndFetchSummary(block1a);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

        assertEquals(1, blockchain.miningBlockTemplate.size());

        AionBlock block2a = blockchain.createBlockAndBlockTemplate(blockchain.getBestBlock(), new ArrayList<>(), false, blockchain.getBestBlock().getTimestamp() + 1000);
        connectResult = blockchain.tryToConnectAndFetchSummary(block2a);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

        assertEquals(2, blockchain.miningBlockTemplate.size());

        // create a side chain block 2b first
        AionBlock block2b = blockchain.createBlockAndBlockTemplate(block1a, new ArrayList<>(), false, block1a.getTimestamp() + 1);
        connectResult = blockchain.tryToConnectAndFetchSummary(block2b);
        assertEquals(ImportResult.IMPORTED_NOT_BEST, connectResult.getLeft());

        assertEquals(3, blockchain.miningBlockTemplate.size());

        AionBlock block3a = blockchain.createBlockAndBlockTemplate(blockchain.getBestBlock(), new ArrayList<>(), false, block2a.getTimestamp() + 10);
        connectResult = blockchain.tryToConnectAndFetchSummary(block3a);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

        assertEquals(4, blockchain.miningBlockTemplate.size());

        // Chain will be branched after import the block3b and the template will be cleaned.
        AionBlock block3b = blockchain.createBlockAndBlockTemplate(block2b, new ArrayList<>(), false, block2b.getTimestamp() + 1);
        connectResult = blockchain.tryToConnectAndFetchSummary(block3b);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());

        assertEquals(0, blockchain.miningBlockTemplate.size());
    }
}
