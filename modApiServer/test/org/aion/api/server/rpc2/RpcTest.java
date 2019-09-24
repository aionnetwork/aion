package org.aion.api.server.rpc2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import org.aion.api.server.AvmPathManager;
import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.avm.provider.schedule.AvmVersionSchedule;
import org.aion.avm.provider.types.AvmConfigurations;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.vm.common.TxNrgRule;
import org.aion.zero.impl.blockchain.AionImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RpcTest {

    private Rpc api;
    private AionImpl impl;

    @Before
    public void setup() {
        // Configure the avm.
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        IEnergyRules energyRules = (t, l) -> {
            if (t == TransactionType.CREATE) {
                return TxNrgRule.isValidNrgContractCreate(l);
            } else {
                return TxNrgRule.isValidNrgTx(l);
            }
        };

        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot, energyRules);

        impl = AionImpl.instForTest();
        impl.aionHub.getBlockchain().setUnityForkNumber(0);
        api = new RpcImpl(impl);
    }

    @After
    public void tearDown() {
        AvmConfigurations.clear();
    }

    @Test
    public void testGetSeed() {

        byte[] seed = api.getseed();

        assertNotNull(seed);
        assertArrayEquals(seed, new byte[64]);
    }


}
