package org.aion.precompiled.contracts;

import java.util.Collections;
import java.util.Random;
import org.aion.precompiled.ExternalCapabilitiesForTesting;
import org.aion.precompiled.ExternalStateForTests;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BenchmarkTest {

    private ContractFactory cf;
    private PrecompiledTransactionContext ctx;

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
        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForTesting());
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void setup() {
        cf = new ContractFactory();
        txHash = getRandom32Bytes();
        origin = new AionAddress(getRandom32Bytes());
        caller = origin;
        blockNumber = 2000001;

        nrgLimit = 20000;

        depth = 0;
    }

    @Test
    public void benchBlake2bHash() {

        ctx =
                new PrecompiledTransactionContext(
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
            ct = cf.getPrecompiledContract(ctx, externalStateForTests);
            ct.execute(txHash, ctx.transactionEnergy);
        }

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < BENCH; i++) {
            ct = cf.getPrecompiledContract(ctx, externalStateForTests);
            ct.execute(txHash, ctx.transactionEnergy);
        }
        System.out.println(
                "Bench blake2b: " + String.valueOf(System.currentTimeMillis() - t1) + "ms");
    }

    private static Random r = new Random();

    private static byte[] getRandom32Bytes() {
        byte[] bytes = new byte[32];
        r.nextBytes(bytes);
        return bytes;
    }

}
