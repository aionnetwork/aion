/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.precompiled.contracts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.aion.base.type.AionAddress;
import org.aion.mcf.config.CfgFork;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.IPrecompiledContract;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BenchmarkTest {

    private ContractFactory cf;
    private ExecutionContext ctx;

    private byte[] txHash, callData;
    private AionAddress origin, caller, blockCoinbase;
    private long blockNumber, blockTimestamp, blockNrgLimit, nrgLimit;
    private DataWord blockDifficulty, nrgPrice, callValue;
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
        BufferedWriter out = new BufferedWriter(
            new FileWriter(forkFile, true));
        out.write("fork0.3.2=2000000");
        out.close();


        cf = new ContractFactory();
        CfgAion.inst();
        txHash = RandomUtils.nextBytes(32);
        origin = AionAddress.wrap(RandomUtils.nextBytes(32));
        caller = origin;
        blockCoinbase = AionAddress.wrap(RandomUtils.nextBytes(32));
        blockNumber = 2000001;
        blockTimestamp = System.currentTimeMillis() / 1000;
        blockNrgLimit = 5000000;
        blockDifficulty = new DataWord(0x100000000L);

        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];

        depth = 0;
        kind = ExecutionContext.CREATE;
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
                new ExecutionContext(
                        txHash,
                        ContractFactory.getBlake2bHashContractAddress(),
                        origin,
                        caller,
                        nrgPrice,
                        nrgLimit,
                        callValue,
                        callData,
                        depth,
                        kind,
                        flags,
                        blockCoinbase,
                        blockNumber,
                        blockTimestamp,
                        blockNrgLimit,
                        blockDifficulty);

        IPrecompiledContract ct;
        // warm up
        for (int i = 0; i < WARMUP; i++) {
            ct = cf.getPrecompiledContract(ctx, null);
            ct.execute(txHash, ctx.getTransactionEnergyLimit());
        }

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < BENCH; i++) {
            ct = cf.getPrecompiledContract(ctx, null);
            ct.execute(txHash, ctx.getTransactionEnergyLimit());
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
        //                nrgPrice,
        //                nrgLimit,
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
        //        IPrecompiledContract ct;
        //        // warm up
        //        for (int i = 0; i < WARMUP; i++) {
        //            ct = cf.getPrecompiledContract(ctx, null);
        //            ct.execute(txHash, ctx.nrgLimit());
        //        }
        //
        //        long t1 = System.currentTimeMillis();
        //        for (int i = 0; i<BENCH; i++) {
        //            ct = cf.getPrecompiledContract(ctx, null);
        //            ct.execute(txHash, ctx.nrgLimit());
        //        }
        //        System.out.println("Bench keccak: " + String.valueOf(System.currentTimeMillis() -
        // t1) + "ms");
    }
}
