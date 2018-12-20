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

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.config.CfgFork;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class Blake2bHashTest {

    private static final long INPUT_NRG = 1000;

    private byte[] byteArray1 = "a0010101010101010101010101".getBytes();
    private byte[] byteArray2 = "1".getBytes();
    private byte[] shortByteArray = "".getBytes();
    private byte[] bigByteArray = new byte[2*1024*1024];
    private byte[] bigByteArray2 = new byte[2*1024*1024+1];
    private Blake2bHashContract blake2bHasher;
    private File forkFile;

    @Before
    public void setUp() throws IOException {
        blake2bHasher = new Blake2bHashContract();

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

        CfgAion.inst();
    }

    @After
    public void teardown() {
        forkFile.delete();
        forkFile.getParentFile().delete();
        forkFile.getParentFile().getParentFile().delete();
    }

    @Test
    public void testBlake256() {
        PrecompiledTransactionResult res = blake2bHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getReturnData();

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);
        String blake2bStr1 = "aa6648de0988479263cf3730a48ef744d238b96a5954aa77d647ae965d3f7715";
        assertEquals(blake2bStr1, ByteUtil.toHexString(output));
    }

    @Test
    public void testBlake256_2() {
        PrecompiledTransactionResult res = blake2bHasher.execute(byteArray2, INPUT_NRG);
        byte[] output = res.getReturnData();

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        String blake2bStr2 = "92cdf578c47085a5992256f0dcf97d0b19f1f1c9de4d5fe30c3ace6191b6e5db";
        assertEquals(blake2bStr2, ByteUtil.toHexString(output));
    }

    @Test
    public void testBlake256_3() {
        PrecompiledTransactionResult res = blake2bHasher.execute(bigByteArray, 2_000_000L);
        byte[] output = res.getReturnData();

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        String blake2bStr2 = "9852d74e002f23d14ba2638b905609419bd16e50843ac147ccf4d509ed2c9dfc";
        assertEquals(blake2bStr2, ByteUtil.toHexString(output));
    }

    @Test
    public void invalidInputLength() {
        PrecompiledTransactionResult res = blake2bHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void invalidInputLength2() {
        PrecompiledTransactionResult res = blake2bHasher.execute(bigByteArray2, INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        byte[] input = Blake2bHashContract.setupInput(0, byteArray1);
        PrecompiledTransactionResult res = blake2bHasher.execute(input, 10);
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res.getResultCode());
    }

    @Test
    public void insufficientNRG2() {
        long nrg = (long) (Math.ceil((float)bigByteArray.length / 4) * 2 + 10);
        PrecompiledTransactionResult res = blake2bHasher.execute(bigByteArray, nrg);
        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());

        res = blake2bHasher.execute(bigByteArray, nrg - 1);
        assertEquals(PrecompiledResultCode.OUT_OF_NRG, res.getResultCode());
    }

    @Test
    public void consistencyTest(){
        byte[] input1 = Blake2bHashContract.setupInput(1, byteArray1);
        byte[] input1Copy = Blake2bHashContract.setupInput(1, byteArray1);
        byte[] input2 = Blake2bHashContract.setupInput(1, byteArray2);

        PrecompiledTransactionResult res1 = blake2bHasher.execute(input1, INPUT_NRG);
        PrecompiledTransactionResult res1Copy = blake2bHasher.execute(input1Copy, INPUT_NRG);
        PrecompiledTransactionResult res2 = blake2bHasher.execute(input2, INPUT_NRG);

        assertThat(res1.getReturnData()).isEqualTo(res1Copy.getReturnData());
        assertThat(res1.getReturnData()).isNotEqualTo(res2.getReturnData());
    }

    @Test
    @Ignore
    public void testInvalidOperation() {
        byte[] input = Blake2bHashContract.setupInput(3, byteArray1);
        PrecompiledTransactionResult res = blake2bHasher.execute(input, INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
    }
}
