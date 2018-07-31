package org.aion.api.server;

import org.aion.api.server.types.CompiledContr;
import org.aion.base.type.Address;
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
    public void TestCreate() {
        System.out.println("run TestApiConnect.");
        ApiImpl api = new ApiImpl();
        api.solcVersion();
        assertNotNull(api.LOG);
    }

    @Test
    public void TestLockAndUnlock() {
        System.out.println("run TestLockAndUnlock.");
        ApiImpl api = new ApiImpl();
        assertFalse(api.unlockAccount(Address.ZERO_ADDRESS(), "testPassword", 0));
        assertFalse(api.unlockAccount(Address.ZERO_ADDRESS().toString(), "testPassword", 0));
        assertFalse(api.lockAccount(Address.ZERO_ADDRESS(), "testPassword"));
    }

    @Test
    public void TestAccountRetrieval() {
        System.out.println("run TestAccountRetrieval.");
        ApiImpl api = new ApiImpl();
        assertTrue(api.getAccounts().isEmpty());
        assertNull(api.getAccountKey(Address.ZERO_ADDRESS().toString()));
    }

    @Test
    public void TestCompileFail() {
        System.out.println("run TestCompileFail.");
        ApiImpl api = new ApiImpl();
        String contract = "This should fail\n";
        assertNotNull(api.contract_compileSolidity(contract).get("compile-error"));
    }

    @Test
    public void TestCompilePass1() {
        System.out.println("run TestCompilePass1.");
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
    public void TestContractCreateResult() {
        System.out.println("run TestContractCreateResult.");
        ApiImpl api = new ApiImpl();
        ApiImpl.ContractCreateResult ccr = api.new ContractCreateResult();
        assertNull(ccr.address);
        assertNull(ccr.transId);
    }

}
