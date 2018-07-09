package org.aion.precompiled.TRS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSqueryContract API.
 */
public class TRSqueryContractTest extends TRShelpers {
    private static final BigInteger DEFAULT_BALANCE = BigInteger.TEN;

    @Before
    public void setup() {
        repo = new DummyRepo();
        ((DummyRepo) repo).storageErrorReturn = null;
        tempAddrs = new ArrayList<>();
    }

    @After
    public void tearDown() {
        for (Address acct : tempAddrs) {
            repo.deleteAccount(acct);
        }
        tempAddrs = null;
        repo = null;
    }

    // <-----------------------------------HELPER METHODS BELOW------------------------------------>

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSqueryContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        ContractExecutionResult res;
        for (int i = 0; i <= useCurrMaxOp; i++) {
            res = trs.execute(input, COST - 1);
            assertEquals(ResultCode.OUT_OF_NRG, res.getCode());
            assertEquals(0, res.getNrgLeft());
        }
    }

    @Test
    public void testTooMuchNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        ContractExecutionResult res;
        for (int i = 0; i <= useCurrMaxOp; i++) {
            res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
            assertEquals(ResultCode.INVALID_NRG_LIMIT, res.getCode());
            assertEquals(0, res.getNrgLeft());
        }
    }

    @Test
    public void testInvalidOperation() {
        //TODO - need all ops implemented first
    }

    // <-----------------------------------IS STARTED TRS TESTS------------------------------------>

    @Test
    public void testIsLiveInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLiveInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLiveInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------------IS LOCKED TRS TESTS------------------------------------>

    @Test
    public void testIsLockedInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x1;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x1;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLockedInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLockedInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------IS DIR DEPO ENABLED TRS TESTS-------------------------------->

    @Test
    public void testIsDepoEnabledInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x2;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x2;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsDirDepoEnabledInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsDirDepoEnabledInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenDisabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenEnabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

}
