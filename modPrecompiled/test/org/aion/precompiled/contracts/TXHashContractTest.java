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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import org.aion.mcf.config.CfgFork;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.fastvm.ExecutionContext;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TXHashContractTest {

    private static final long INPUT_NRG = 1000;
    private PrecompiledContract tXHashContract;
    private byte[] txHash = RandomUtils.nextBytes(32);
    private File forkFile;



    @Before
    public void setUp() throws IOException {

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
        ExecutionContext ctx = new ExecutionContext(null, txHash,
            ContractFactory.getTxHashContractAddress(), null, null, null,
            0L, null, null, 0, 0, 0, null,
            2000001L, 0L, 0L,
            null);

        tXHashContract = new ContractFactory().getPrecompiledContract(ctx, null);
    }

    @After
    public void teardown() {
        forkFile.delete();
        forkFile.getParentFile().delete();
        forkFile.getParentFile().getParentFile().delete();
    }

    @Test
    public void testgetTxHash() {
        TransactionResult res = tXHashContract.execute(null, INPUT_NRG);

        System.out.println(res.toString());
        assertTrue(Arrays.equals(txHash, res.getOutput()));
    }

    @Test
    public void testgetTxHashOutofNrg() {
        TransactionResult res = tXHashContract.execute(null, COST - 1);

        System.out.println(res.toString());
        assertEquals(PrecompiledResultCode.OUT_OF_NRG.toInt(), res.getResultCode().toInt());
    }
}
