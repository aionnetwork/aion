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

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.aion.precompiled.contracts.TXHashContract.COST;

import java.util.Arrays;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

public class TXHashTest {

    private static final long INPUT_NRG = 1000;
    private IPrecompiledContract tXHashContract;
    private byte[] txHash = RandomUtils.nextBytes(32);


    @Before
    public void setUp() {
        ExecutionContext ctx = new ExecutionContext(txHash,
            ContractFactory.getTxHashContractAddress(), null, null, null,
            0L, null, null, 0, 0, 0, null,
            0L, 0L, 0L,
            null);

        tXHashContract = new ContractFactory().getPrecompiledContract(ctx, null);
    }

    @Test
    public void testgetTxHash() {
        ExecutionResult res = (ExecutionResult) tXHashContract.execute(null, INPUT_NRG);

        System.out.println(res.toString());
        assertTrue(Arrays.equals(txHash, res.getOutput()));
    }

    @Test
    public void testgetTxHashOutofNrg() {
        ExecutionResult res = (ExecutionResult) tXHashContract.execute(null, COST - 1);

        System.out.println(res.toString());
        assertEquals(ResultCode.OUT_OF_NRG.toInt(), res.getResultCode().toInt());
    }
}
