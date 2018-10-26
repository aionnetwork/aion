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

import static junit.framework.TestCase.assertEquals;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.precompiled.contracts.Blake2bHashContract;
import org.aion.precompiled.contracts.KeccakHash;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.junit.Before;
import org.junit.Test;

public class HashTest {

    private static final long INPUT_NRG = 1000;

    private byte[] byteArray1 = "a0010101010101010101010101".getBytes();
    private byte[] shortByteArray = "".getBytes();
    private Blake2bHashContract blake2bHasher;
    private KeccakHash keccakHasher;

    @Before
    public void setUp() {
        blake2bHasher = new Blake2bHashContract();
        keccakHasher = new KeccakHash();
    }

    @Test
    public void testBlake256() {
        byte[] input = Blake2bHashContract.setupInput(0, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        System.out.println("The blake256 hash for '" + new String(byteArray1,
            StandardCharsets.UTF_8) + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }

    @Test
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
    public void testKeccak256() {
        ExecutionResult res = keccakHasher.execute(byteArray1, INPUT_NRG);
        byte[] output = res.getOutput();

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(32, output.length);

        System.out.println("The keccak256 hash for '" + new String(byteArray1,
            StandardCharsets.UTF_8) + "' is:");
        System.out.print("      ");
        for (byte b : output) {
            System.out.print(b + " ");
        }
        System.out.println();
    }

    @Test
    public void invalidInputLength() {
        byte[] input = Blake2bHashContract.setupInput(0, shortByteArray);
        ExecutionResult res = blake2bHasher.execute(input, INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());

        ExecutionResult res2 = keccakHasher.execute(shortByteArray, INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, res2.getResultCode());
    }

    @Test
    public void insufficientNRG() {
        byte[] input = Blake2bHashContract.setupInput(0, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, 100);
        assertEquals(ResultCode.OUT_OF_NRG, res.getResultCode());

        ExecutionResult res2 = keccakHasher.execute(byteArray1, 100);
        assertEquals(ResultCode.OUT_OF_NRG, res2.getResultCode());
    }

    @Test
    public void testInvalidOperation() {
        byte[] input = Blake2bHashContract.setupInput(3, byteArray1);
        ExecutionResult res = blake2bHasher.execute(input, INPUT_NRG);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
    }

    private static IRepositoryConfig repoConfig =
        new IRepositoryConfig() {
            @Override
            public String getDbPath() {
                return "";
            }

            @Override
            public IPruneConfig getPruneConfig() {
                return new CfgPrune(false);
            }

            @Override
            public IContractDetails contractDetailsImpl() {
                return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
            }

            @Override
            public Properties getDatabaseConfig(String db_name) {
                Properties props = new Properties();
                props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                return props;
            }
        };
}
