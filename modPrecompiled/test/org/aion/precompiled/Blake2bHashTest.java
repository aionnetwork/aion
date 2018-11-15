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
package org.aion.precompiled;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.nio.charset.StandardCharsets;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.hash.Blake2b;
import org.aion.precompiled.contracts.Blake2bHashContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.junit.Assert;
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

    @Before
    public void setUp() {
        blake2bHasher = new Blake2bHashContract();
    }

    @Test
    public void testBlake256() {
        ExecutionResult res = blake2bHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);
        String blake2bStr1 = "aa6648de0988479263cf3730a48ef744d238b96a5954aa77d647ae965d3f7715";
        assertEquals(blake2bStr1, ByteUtil.toHexString(output));
    }

    @Test
    public void testBlake256_2() {
        ExecutionResult res = blake2bHasher.execute(byteArray2, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        String blake2bStr2 = "92cdf578c47085a5992256f0dcf97d0b19f1f1c9de4d5fe30c3ace6191b6e5db";
        assertEquals(blake2bStr2, ByteUtil.toHexString(output));
    }

    @Test
    public void testBlake256_3() {
        ExecutionResult res = blake2bHasher.execute(bigByteArray, 2_000_000L);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        String blake2bStr2 = "9852d74e002f23d14ba2638b905609419bd16e50843ac147ccf4d509ed2c9dfc";
        assertEquals(blake2bStr2, ByteUtil.toHexString(output));
    }

    @Test
    @Ignore
    public void testBlake128() {
        byte[] input = Blake2bHashContract.setupInput(1, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(16, output.length);

        System.out.println("The blake128 hash for '" + new String(byteArray1,
            StandardCharsets.UTF_8) + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }


    @Test
    public void invalidInputLength() {
        ExecutionResult res = blake2bHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void invalidInputLength2() {
        ExecutionResult res = blake2bHasher.execute(bigByteArray2, INPUT_NRG);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        byte[] input = Blake2bHashContract.setupInput(0, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, 10);
        assertEquals(ResultCode.OUT_OF_NRG, res.getResultCode());
    }

    @Test
    public void insufficientNRG2() {
        long nrg = (long) (Math.ceil((float)bigByteArray.length / 4) * 2 + 10);
        ExecutionResult res = blake2bHasher.execute(bigByteArray, nrg);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());

        res = blake2bHasher.execute(bigByteArray, nrg - 1);
        assertEquals(ResultCode.OUT_OF_NRG, res.getResultCode());
    }

    @Test
    public void consistencyTest(){
        byte[] input1 = Blake2bHashContract.setupInput(1, byteArray1);
        byte[] input1Copy = Blake2bHashContract.setupInput(1, byteArray1);
        byte[] input2 = Blake2bHashContract.setupInput(1, byteArray2);

        ExecutionResult res1 = blake2bHasher.execute(input1, INPUT_NRG);
        ExecutionResult res1Copy = blake2bHasher.execute(input1Copy, INPUT_NRG);
        ExecutionResult res2 = blake2bHasher.execute(input2, INPUT_NRG);

        assertThat(res1.getOutput()).isEqualTo(res1Copy.getOutput());
        assertThat(res1.getOutput()).isNotEqualTo(res2.getOutput());
    }

    @Test
    @Ignore
    public void testInvalidOperation() {
        byte[] input = Blake2bHashContract.setupInput(3, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
    }
}
