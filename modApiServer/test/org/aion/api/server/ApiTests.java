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

package org.aion.api.server;

import org.aion.api.server.types.CompiledContr;
import org.aion.base.type.Address;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.types.AbstractBlock;
import org.junit.Test;


import java.math.BigInteger;
import java.util.Map;

import static org.junit.Assert.*;

public class ApiTests {

    private class ApiImpl extends Api {

        @Override
        public String getCoinbase() {
            return null;
        }

        @Override
        public byte getApiVersion() {
            return 0;
        }

        @Override
        public AbstractBlock getBestBlock() {
            return null;
        }

        @Override
        public AbstractBlock<?, ?> getBlock(long _bn) {
            return null;
        }

        @Override
        public AbstractBlock<?, ?> getBlockByHash(byte[] hash) {
            return null;
        }

        @Override
        public BigInteger getBalance(String _address) throws Exception {
            return null;
        }

        public ApiImpl() {
            super(true);
        }
    }

    @Test
    public void testCreate() {
        ApiImpl api = new ApiImpl();
        api.solcVersion();
        assertNotNull(api.LOG);
    }

    @Test
    public void testLockAndUnlock() {
        ApiImpl api = new ApiImpl();
        assertFalse(api.unlockAccount(Address.ZERO_ADDRESS(), "testPassword", 0));
        assertFalse(api.unlockAccount(Address.ZERO_ADDRESS().toString(), "testPassword", 0));
        assertFalse(api.lockAccount(Address.ZERO_ADDRESS(), "testPassword"));

        Address addr = new Address(Keystore.create("testPwd"));
        assertTrue(api.unlockAccount(addr, "testPwd", 50000));
    }

    @Test
    public void testAccountRetrieval() {
        ApiImpl api = new ApiImpl();
        assertNull(api.getAccountKey(Address.ZERO_ADDRESS().toString()));

        String addr = Keystore.create("testPwd");
        assertEquals(AccountManager.inst().getKey(Address.wrap(addr)), api.getAccountKey(addr));

        assertTrue(api.getAccounts().contains(addr));
    }

    @Test
    public void testCompileFail() {
        ApiImpl api = new ApiImpl();
        String contract = "This should fail\n";
        assertNotNull(api.contract_compileSolidity(contract).get("compile-error"));
    }

    @Test
    public void testCompilePass1() {
        ApiImpl api = new ApiImpl();

        // Taken from FastVM CompilerTest.java
        String contract = "pragma solidity ^0.4.0;\n" + //
                "\n" + //
                "contract SimpleStorage {\n" + //
                "    uint storedData;\n" + //
                "\n" + //
                "    function set(uint x) {\n" + //
                "        storedData = x;\n" + //
                "    }\n" + //
                "\n" + //
                "    function get() constant returns (uint) {\n" + //
                "        return storedData;\n" + //
                "    }\n" + //
                "}";
        Map<String, CompiledContr> compileResult = api.contract_compileSolidity(contract);
        CompiledContr compiledContr = compileResult.get("SimpleStorage");
        assertNull(compiledContr.error);
    }

    @Test
    public void testContractCreateResult() {
        ApiImpl api = new ApiImpl();
        ApiImpl.ContractCreateResult ccr = api.new ContractCreateResult();
        assertNull(ccr.address);
        assertNull(ccr.transId);
    }

}