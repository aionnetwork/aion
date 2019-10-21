package org.aion.zero.impl.blockchain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Test;

public class AionBlockchainImplTest {

    private StandaloneBlockchain blockchain;

    @After
    public void tearDown() {

        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork0.5.0", String.valueOf(Long.MAX_VALUE));
        cfg.getFork().setProperties(p);
        this.blockchain = null;
    }

    @Test
    public void testUnityMininmumNumber() {
        CfgAion cfg = CfgAion.inst();
        Properties p = new Properties();
        p.put("fork0.5.0", "1");
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
        p.put("fork0.5.0", "2");
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
}
