package org.aion.zero.impl.precompiled.contracts;

import java.util.Collections;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.zero.impl.precompiled.ExternalStateForTests;
import org.aion.zero.impl.vm.precompiled.ExternalCapabilitiesForPrecompiled;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BenchmarkTest {

    private ContractFactory cf;

    private byte[] txHash;
    private AionAddress origin;
    private AionAddress caller;
    private long blockNumber;
    private long nrgLimit;
    private int depth;

    private static int WARMUP = 2000;
    private static int BENCH = 1000000;

    @BeforeClass
    public static void setupCapabilities() {
        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForPrecompiled());
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void setup() {
        cf = new ContractFactory();
        txHash = RandomUtils.nextBytes(32);
        origin = new AionAddress(RandomUtils.nextBytes(32));
        caller = origin;
        blockNumber = 2000001;

        nrgLimit = 20000;

        depth = 0;
    }

    @Test
    public void benchBlake2bHash() {

        PrecompiledTransactionContext context = new PrecompiledTransactionContext(
            ContractInfo.BLAKE_2B.contractAddress,
            origin,
            caller,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            txHash,
            txHash,
            blockNumber,
            nrgLimit,
            depth);

        ExternalStateForTests externalStateForTests = ExternalStateForTests.usingDefaultRepository();

        PrecompiledContract ct;
        // warm up
        for (int i = 0; i < WARMUP; i++) {
            ct = cf.getPrecompiledContract(context, externalStateForTests);
            ct.execute(txHash, context.transactionEnergy);
        }

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < BENCH; i++) {
            ct = cf.getPrecompiledContract(context, externalStateForTests);
            ct.execute(txHash, context.transactionEnergy);
        }
        System.out.println(
                "Bench blake2b: " + String.valueOf(System.currentTimeMillis() - t1) + "ms");
    }
}
