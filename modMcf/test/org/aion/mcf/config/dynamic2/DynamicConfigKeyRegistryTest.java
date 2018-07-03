package org.aion.mcf.config.dynamic2;

import org.aion.mcf.config.applier.MiningApplier;
import org.aion.zero.impl.config.CfgConsensusPow;
import org.junit.Test;

import static org.junit.Assert.*;

public class DynamicConfigKeyRegistryTest {
    @Test
    public void test() {
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry();
//        unit.bind("aion.consensus.mining", cfg -> cfg.getConsensus().getMining(), new MiningApplier());
    }

}