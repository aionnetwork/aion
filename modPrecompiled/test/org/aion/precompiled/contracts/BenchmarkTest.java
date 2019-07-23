package org.aion.precompiled.contracts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import org.aion.mcf.config.CfgFork;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BenchmarkTest {

    private ContractFactory cf;
    private PrecompiledTransactionContext ctx;

    private byte[] txHash, callData;
    private AionAddress origin, caller, blockCoinbase;
    private long blockNumber, blockTimestamp, blockNrgLimit, nrgLimit;
    private DataWordImpl blockDifficulty, nrgPrice, callValue;
    private int depth, kind, flags;

    private static int WARMUP = 2000;
    private static int BENCH = 1000000;
    private File forkFile;

    @Before
    public void setup() throws IOException {

        new File(System.getProperty("user.dir") + "/mainnet/config").mkdirs();
        forkFile =
                new File(
                        System.getProperty("user.dir")
                                + "/mainnet/config"
                                + CfgFork.FORK_PROPERTIES_PATH);
        forkFile.createNewFile();

        // Open given file in append mode.
        BufferedWriter out = new BufferedWriter(new FileWriter(forkFile, true));
        out.write("fork0.3.2=2000000");
        out.close();

        cf = new ContractFactory();
        CfgAion.inst();
        txHash = RandomUtils.nextBytes(32);
        origin = new AionAddress(RandomUtils.nextBytes(32));
        caller = origin;
        blockCoinbase = new AionAddress(RandomUtils.nextBytes(32));
        blockNumber = 2000001;
        blockTimestamp = System.currentTimeMillis() / 1000;
        blockNrgLimit = 5000000;
        blockDifficulty = new DataWordImpl(0x100000000L);

        nrgPrice = DataWordImpl.ONE;
        nrgLimit = 20000;
        callValue = DataWordImpl.ZERO;
        callData = new byte[0];

        depth = 0;
        kind = PrecompiledTransactionContext.CREATE;
        flags = 0;
    }

    @After
    public void teardown() {
        forkFile.delete();
        forkFile.getParentFile().delete();
        forkFile.getParentFile().getParentFile().delete();
    }

    @Test
    public void benchBlack2bHash() {

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

        PrecompiledContract ct;
        // warm up
        for (int i = 0; i < WARMUP; i++) {
            ct = cf.getPrecompiledContract(ctx, null);
            ct.execute(txHash, ctx.transactionEnergy);
        }

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < BENCH; i++) {
            ct = cf.getPrecompiledContract(ctx, null);
            ct.execute(txHash, ctx.transactionEnergy);
        }
        System.out.println(
                "Bench blake2b: " + String.valueOf(System.currentTimeMillis() - t1) + "ms");
    }

    @Test
    public void benchKeccakHash() {
        //        ctx =
        //            new ExecutionContext(
        //                txHash,
        //                ContractFactory.getKeccakHashContractAddress(),
        //                origin,
        //                caller,
        //                getEnergyPrice,
        //                getEnergyLimit,
        //                callValue,
        //                callData,
        //                depth,
        //                kind,
        //                flags,
        //                blockCoinbase,
        //                blockNumber,
        //                blockTimestamp,
        //                blockNrgLimit,
        //                blockDifficulty);
        //
        //        PrecompiledContract ct;
        //        // warm up
        //        for (int i = 0; i < WARMUP; i++) {
        //            ct = cf.getPrecompiledContract(ctx, null);
        //            ct.execute(txHash, ctx.getEnergyLimit());
        //        }
        //
        //        long t1 = System.currentTimeMillis();
        //        for (int i = 0; i<BENCH; i++) {
        //            ct = cf.getPrecompiledContract(ctx, null);
        //            ct.execute(txHash, ctx.getEnergyLimit());
        //        }
        //        System.out.println("Bench keccak: " + String.valueOf(System.currentTimeMillis() -
        // t1) + "ms");
    }
}
