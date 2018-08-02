package org.aion.precompiled.TRS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSstateContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSstateContract API.
 */
public class TRSstateContractTest extends TRShelpers {
    private static final int MAX_OP = 3;

    @Before
    public void setup() throws InterruptedException {
        repo = new DummyRepo();
        ((DummyRepo) repo).storageErrorReturn = null;
        tempAddrs = new ArrayList<>();
        repo.addBalance(AION, BigInteger.ONE);
        createBlockchain(0, 0);
    }

    @After
    public void tearDown() {
        for (Address acct : tempAddrs) {
            repo.deleteAccount(acct);
        }
        tempAddrs = null;
        repo = null;
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSstateContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateInsufficientNrg() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST - 1);
        assertEquals(ResultCode.OUT_OF_NRG, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateTooMuchNrg() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
        assertEquals(ResultCode.INVALID_NRG_LIMIT, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInvalidOperation() {
        Address addr = getNewExistentAccount(BigInteger.ONE);
        TRSstateContract trs = newTRSstateContract(addr);
        byte[] input = new byte[DoubleDataWord.BYTES];
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            if ((i < 0) || (i > MAX_OP)) {
                input[0] = (byte) i;
                assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
            }
        }
    }

    // <-------------------------------------CREATE TRS TESTS-------------------------------------->

    @Test
    public void testCreateTestModeNotAion() {
        // Test isDirectDeposit false
        byte[] input = getCreateInput(true, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test isDirectDeposit true
        input = getCreateInput(true, true, 1, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateZeroPeriods() {
        byte[] input = getCreateInput(false, false, 0, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePeriodsTooLarge() {
        // Smallest too-large period: 1201
        byte[] input = getCreateInput(false, false, 1201, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Largest too-large period: 65,535
        input = getCreateInput(false, false, 65_535, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePrecisionTooHigh() {
        // Smallest too-large precision: 19
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 19);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Largest too-large precision: 255
        input = getCreateInput(false, false, 1, BigInteger.ZERO, 255);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateCorrectOwner() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = new Address(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(caller, getOwner(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTestModeCorrectOwner() {
        byte[] input = getCreateInput(true, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(AION);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        assertEquals(AION, getOwner(trs, new Address(res.getOutput())));
    }

    @Test
    public void testCreateVerifyPeriods() {
        // Test on min periods.
        int periods = 1;
        byte[] input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTestModeVerifyPeriods() {
        // Test on min periods.
        int periods = 1;
        byte[] input = getCreateInput(true, false, periods, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(AION);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(AION);
        repo.flush();

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(true, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyPeriodsByteBoundary() {
        // Tests periods that have their leading bits around the 8-9th bits (where the 2 bytes meet).
        int periods = 0xFF;
        int periods2 = periods + 1;
        int periods3 = 0x1FF;

        byte[] input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        input = getCreateInput(false, false, periods2, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods2, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        input = getCreateInput(false, false, periods3, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods3, getPeriods(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyIsTest() {
        // When false.
        boolean isTest = false;
        byte[] input = getCreateInput(isTest, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isTest, isTestContract(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // When true
        isTest = true;
        input = getCreateInput(isTest, false, 1, BigInteger.ZERO, 0);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isTest, isTestContract(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyIsDirectDeposit() {
        // When false
        boolean isDirectDeposit = false;
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, isDirectDeposit, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isDirectDeposit, isDirectDepositEnabled(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // When true
        isDirectDeposit = true;
        input = getCreateInput(false, isDirectDeposit, 1, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isDirectDeposit, isDirectDepositEnabled(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyZeroPercentage() {
        // Test no shifts.
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigDecimal.ZERO, getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test 18 shifts.
        input = getCreateInput(false, false, 1, BigInteger.ZERO, 18);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigDecimal.ZERO.movePointLeft(18), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyOneHundredPercentage() {
        // Test no shifts.
        BigInteger raw = new BigInteger("100");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test 18 shifts.
        raw = new BigInteger("100000000000000000000");
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateAboveOneHundredPercentage() {
        // Test no shifts.
        BigInteger raw = new BigInteger("101");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test 18 shifts.
        raw = new BigInteger("100000000000000000001");
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test all 9 bytes of percent as 1's using zero shifts.
        byte[] rawBytes = new byte[]{ (byte) 0x0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        raw = new BigInteger(rawBytes);
        input = getCreateInput(false, false, 1, raw, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test all 9 bytes of percent as 1's using 18 shifts -> 4722.36% still no good.
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateVerify18DecimalPercentage() {
        // This is: 99.999999999999999999 with 18 shifts ... verify it is ok and we get it back
        BigInteger raw = new BigInteger("99999999999999999999");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 18);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // This is: 73.749309184932750917 with 18 shifts ... verify we get it back
        raw = new BigInteger("73749309184932750917");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // This is: 0.000000000000000001 with 18 shifts ... verify we get it back
        raw = new BigInteger("0000000000000000001");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateValidAndInvalidPercentagesByShifting() {
        // With no shifting we have: 872743562198734% -> no good
        int shifts = 0;
        BigInteger raw = new BigInteger("872743562198734");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, shifts);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // With 12 shifts we have: 872.743562198734% -> no good
        shifts = 12;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // With 13 shifts we have: 87.2743562198734% -> good
        shifts = 13;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // With 14 shifts we have: 8.72743562198734% -> good
        shifts = 14;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // With 18 shifts we have: 000.000872743562198734% -> good
        shifts = 18;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTRSaddressDeterministic() {
        // The TRS contract address returned must be deterministic, based on owner's addr + nonce.
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        tempAddrs.add(contract);

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        // Different caller, should be different addr returned.
        trs = newTRSstateContract(getNewExistentAccount(BigInteger.ZERO));
        res = trs.execute(input, COST);

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));
        contract = Address.wrap(res.getOutput());

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        // Same caller as original & nonce hasn't changed, should be same contract addr returned.
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(contract, Address.wrap(res.getOutput()));

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        // Same caller as original but nonce changed, should be different addr returned.
        repo.incrementNonce(caller);
        repo.flush();
        trs = newTRSstateContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));

        contract = Address.wrap(res.getOutput());
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
    }

    @Test
    public void testCreateSuccessNrgLeft() {
        long diff = 540;
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(caller);
        ExecutionResult res = trs.execute(input, COST + diff);
        Address contract = Address.wrap(res.getOutput());
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(diff, res.getNrgLeft());
        tempAddrs.add(contract);

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
    }

    @Test
    public void testCreateContractHasTimestamp() {
        Address caller = getNewExistentAccount(BigInteger.ONE);
        byte[] input = getCreateInput(false, true, 1, BigInteger.ZERO, 0);
        TRSstateContract trs = newTRSstateContract(caller);

        long before = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        ExecutionResult res = trs.execute(input, COST);
        long after = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        Address contract = Address.wrap(res.getOutput());
        long timestamp = getContractTimestamp(trs, contract);

        assertTrue(timestamp >= before);
        assertTrue(timestamp <= after);
    }

    // <-------------------------------------LOCK TRS TESTS---------------------------------------->

    @Test
    public void testLockAddressTooSmall() {
        // Test on empty address.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[1];
        input[0] = (byte) 0x1;
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on address size 31.
        input = new byte[32];
        input[0] = (byte) 0x1;
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockAddressTooLarge() {
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[34];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockNotTRScontractAddress() {
        // Test attempt to lock a regular account.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(acct);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test attempt to lock what looks like a TRS address (proper prefix).
        input = getLockInput(contract);
        input[input.length - 1] = (byte) ~input[input.length - 1];
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockNotContractOwner() {
        // Test not in test mode: owner is acct, caller is AION.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(contract);
        TRSstateContract trs = newTRSstateContract(AION);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode: owner is AION, caller is acct.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);
        input = getLockInput(contract);
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockButContractAlreadyLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        // Deposit into contract so we can lock it.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        input = getLockInput(contract);
        trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));    // lock worked.
        assertFalse(isContractLive(trs, contract));

        // attempt to lock the contract again...
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        // Deposit into contract so we can lock it.
        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        input = getLockInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));    // lock worked.
        assertFalse(isContractLive(trs, contract));

        // attempt to lock the contract again...
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockAndVerifyIsLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        // Deposit into contract so we can lock it.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        input = getLockInput(contract);
        trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));    // lock worked.
        assertFalse(isContractLive(trs, contract));

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        // Deposit into contract so we can lock it.
        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());

        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        input = getLockInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));    // lock worked.
        assertFalse(isContractLive(trs, contract));
    }

    @Test
    public void testLockButContractIsLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getStartInput(contract);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLive(trs, contract));

        // Attempt to lock live contract.
        input = getLockInput(contract);
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        input = getStartInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLive(trs, contract));

        // Attempt to lock live contract.
        input = getLockInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockWithContractBalanceZero() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(contract);
        ExecutionResult res = newTRSstateContract(owner).execute(input, COST);
        AbstractTRS trs = newTRSstateContract(owner);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(isContractLocked(trs, contract));

        // Now deposit into contract and refund it so we are back at zero and try again.
        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(owner).execute(input, COST).getResultCode());
        input = getRefundInput(contract, owner, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(owner).execute(input, COST).getResultCode());

        input = getLockInput(contract);
        res = newTRSstateContract(owner).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(isContractLocked(trs, contract));
    }

    // <-------------------------------------START TRS TESTS--------------------------------------->

    @Test
    public void testStartAddressTooSmall() {
        // Test on empty address.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        createAndLockTRScontract(acct, false, false, 1, BigInteger.ZERO,
            0);
        byte[] input = new byte[1];
        input[0] = (byte) 0x1;
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on address size 31.
        input = new byte[32];
        input[0] = (byte) 0x1;
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartAddressTooLarge() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false,
            1, BigInteger.ZERO, 0);
        byte[] input = new byte[34];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

    }

    @Test
    public void testStartNotTRScontractAddress() {
        // Test regular address as a TRS address.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false,
            1, BigInteger.ZERO, 0);
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        repo.incrementNonce(acct);
        repo.flush();

        // Test what looks like a proper TRS address (has TRS prefix).
        input[input.length - 1] = (byte) ~input[input.length - 1];  // flip a bit
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartNotContractOwner() {
        // Test not in test mode: owner is acct, caller is AION.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(contract);
        TRSstateContract trs = newTRSstateContract(AION);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode: owner is AION, caller is acct.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);
        input = getLockInput(contract);
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartButContractAlreadyLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getStartInput(contract);
        TRSstateContract trs = newTRSstateContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));
        assertTrue(isContractLive(trs, contract));  // contract is live now.

        // Try to start contract again.
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        input = getStartInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));
        assertTrue(isContractLive(trs, contract));  // contract is live now.

        // Try to start contract again.
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartButContractNotLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        assertFalse(isContractLocked(trs, contract));

        byte[] input = getStartInput(contract);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        assertFalse(isContractLocked(trs, contract));

        input = getStartInput(contract);
        trs = newTRSstateContract(acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartAndVerifyIsLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        assertTrue(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        byte[] input = getStartInput(contract);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));
        assertTrue(isContractLive(trs, contract));  // true now.

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        assertTrue(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        input = getStartInput(contract);
        trs = newTRSstateContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertTrue(isContractLocked(trs, contract));
        assertTrue(isContractLive(trs, contract));  // true now.
    }

    @Test
    public void testBonusBalanceNonExistentContract() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        assertEquals(BigInteger.ZERO, getBonusBalance(newTRSstateContract(acct), acct));
    }

    @Test
    public void testBonusBalanceOnContractNotYetLive() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Deposit bonus funds into contract. Until it is live the contract does not see these funds.
        repo.addBalance(contract, new BigInteger("100"));
        assertEquals(BigInteger.ZERO, getBonusBalance(newTRSstateContract(acct), contract));

        // Now deposit regularly and lock the contract. Again we should still not see the bonus yet.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.ZERO, getBonusBalance(newTRSstateContract(acct), contract));
    }

    @Test
    public void testStartZeroBonusBalance() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Deposit regularly and lock contract. We have no bonus deposits in. Start contract.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());
        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        assertEquals(BigInteger.ZERO, getBonusBalance(newTRSstateContract(acct), contract));
    }

    @Test
    public void testStartNonZeroBonusBalance() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        BigInteger bonus1 = new BigInteger("23");
        BigInteger bonus2 = new BigInteger("346234");
        BigInteger bonus3 = new BigInteger("98234623");

        repo.addBalance(contract, bonus1);  // make a bonus deposit.

        // Deposit regularly and lock contract. We have no bonus deposits in. Start contract.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        repo.addBalance(contract, bonus2);  // make a bonus deposit.

        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        repo.addBalance(contract, bonus3);  // this deposit should be ignored.

        assertEquals(bonus1.add(bonus2), getBonusBalance(newTRSstateContract(acct), contract));
    }

    @Test
    public void testStartNonZeroBonusBalanceUsesMultipleRows() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        BigInteger bonus = BigInteger.TWO.pow(300);

        repo.addBalance(contract, bonus);  // make a bonus deposit.

        // Deposit regularly and lock contract. We have no bonus deposits in. Start contract.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        repo.addBalance(contract, bonus);  // make a bonus deposit.

        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        repo.addBalance(contract, bonus);  // this deposit should be ignored.

        assertEquals(bonus.add(bonus), getBonusBalance(newTRSstateContract(acct), contract));
    }

    @Test
    public void testTotalOwed1() {
        // acct is only depositor in the contract so he should be owed his full deposit + all bonus funds.
        BigInteger deposit = BigInteger.TWO.pow(300);
        BigInteger bonus = BigInteger.TWO.pow(222).subtract(BigInteger.ONE);

        Address acct = getNewExistentAccount(deposit);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, deposit);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, acct);
        assertEquals(deposit.add(bonus), getTotalOwed(newTRSstateContract(acct), contract, acct));
    }

    @Test
    public void testTotalOwed2() {
        // we check the owings are distributed correctly. We pass in the fraction of the total wealth
        // that 1 of the 3 depositors holds. The rest is taken care of.
        checkOwings(79);
        checkOwings(99);
        checkOwings(1);
        checkOwings(21);
        checkOwings(33);
        checkOwings(0);
    }

    @Test
    public void testTotalOwed3() {
        // acct owns an incredibly small portion of the wealth. We want to make sure in such an
        // unequal setting the account still cannot lose its deposit.
        BigInteger deposit1 = BigInteger.ONE;
        BigInteger deposit2 = BigInteger.TWO.pow(317);

        // With these differences we expect that if the bonus deposit is equal to the sum of these
        // 2 deposits then acct will be owed 2 tokens. If the bonus deposit is equal to 1 less than
        // this sum then acct will be owed 1 token and we will have 1 outstanding unclaimed bonus
        // token. We test both scenarios.
        BigInteger sum = deposit1.add(deposit2);
        checkOwingsGivenDepositsAndBonus(deposit1, deposit2, sum);
        checkOwingsGivenDepositsAndBonus(deposit1, deposit2, sum.subtract(BigInteger.valueOf(1)));

        // A 1-10 inequality against a very large bonus deposit.
        checkOwingsGivenDepositsAndBonus(deposit1, BigInteger.TEN, deposit2);
    }

    // <----------------------------------OPEN FUNDS TRS TESTS------------------------------------->

    @Test
    public void testOpenFundsInputTooShort() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getOpenFundsInput(contract);
        byte[] shortInput = Arrays.copyOf(input, input.length - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testOpenFundsInputTooLong() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getOpenFundsInput(contract);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testOpenFundsCallerIsNotOwner() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address other = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(other).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsContractIsLive() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createLockedAndLiveTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsContractNonExistent() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        byte[] input = getOpenFundsInput(acct);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsContractNotYetLocked() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));
    }

    @Test
    public void testOpenFundsContractLockedNotYetLive() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        assertFalse(getAreContractFundsOpen(trs, contract));
        assertTrue(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));

        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));
        assertFalse(isContractLocked(trs, contract));
        assertFalse(isContractLive(trs, contract));
    }

    @Test
    public void testOpenFundsMultipleTimes() {
        // Currently any subsequent attempts are thwarted.
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsWithdrawIsNowWithdrawAll() throws InterruptedException {
        createBlockchain(0, 0);
        BigInteger bal1 = new BigInteger("2375628376523");
        BigInteger bal2 = new BigInteger("438756347565782346578");
        BigInteger bal3 = new BigInteger("98124329685948546");
        BigInteger bonus = new BigInteger("325467523673432535248233278324346");
        Address acct1 = getNewExistentAccount(bal1);
        Address acct2 = getNewExistentAccount(bal2);
        Address acct3 = getNewExistentAccount(bal3);
        Address contract = createTRScontract(acct1, false, true, 100,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, bal1);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal2);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal3);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getResultCode());
        repo.addBalance(contract, bonus);
        assertEquals(BigInteger.ZERO, repo.getBalance(acct1));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct3));

        // Now open the funds.
        AbstractTRS trs = newTRSstateContract(acct1);
        input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now each account should make 1 withdrawal and receive their total owings.
        BigDecimal total = new BigDecimal(bal1.add(bal2).add(bal3));
        BigInteger owings1 = grabOwings(new BigDecimal(bal1), total, new BigDecimal(bonus));
        BigInteger owings2 = grabOwings(new BigDecimal(bal2), total, new BigDecimal(bonus));
        BigInteger owings3 = grabOwings(new BigDecimal(bal3), total, new BigDecimal(bonus));
        input = getWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getResultCode());
        assertEquals(owings1, repo.getBalance(acct1));
        assertEquals(owings2, repo.getBalance(acct2));
        assertEquals(owings3, repo.getBalance(acct3));

        // A subsequent withdraw operation should not withdraw anything now.
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct3).execute(input, COST).getResultCode());
        assertEquals(owings1, repo.getBalance(acct1));
        assertEquals(owings2, repo.getBalance(acct2));
        assertEquals(owings3, repo.getBalance(acct3));
    }

    @Test
    public void testOpenFundsBulkWithdrawIsNowBulkWithdrawAll() throws InterruptedException {
        createBlockchain(0, 0);
        BigInteger bal1 = new BigInteger("4366234645");
        BigInteger bal2 = new BigInteger("5454757853");
        BigInteger bal3 = new BigInteger("43534654754342");
        BigInteger bonus = new BigInteger("546547542332523534");
        Address acct1 = getNewExistentAccount(bal1);
        Address acct2 = getNewExistentAccount(bal2);
        Address acct3 = getNewExistentAccount(bal3);
        Address contract = createTRScontract(acct1, false, true, 100,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, bal1);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal2);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal3);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getResultCode());
        repo.addBalance(contract, bonus);
        assertEquals(BigInteger.ZERO, repo.getBalance(acct1));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct3));

        // Now open the funds.
        AbstractTRS trs = newTRSstateContract(acct1);
        input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // A bulk withdraw will withdraw all funds for all contributors.
        BigDecimal total = new BigDecimal(bal1.add(bal2).add(bal3));
        BigInteger owings1 = grabOwings(new BigDecimal(bal1), total, new BigDecimal(bonus));
        BigInteger owings2 = grabOwings(new BigDecimal(bal2), total, new BigDecimal(bonus));
        BigInteger owings3 = grabOwings(new BigDecimal(bal3), total, new BigDecimal(bonus));
        input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        assertEquals(owings1, repo.getBalance(acct1));
        assertEquals(owings2, repo.getBalance(acct2));
        assertEquals(owings3, repo.getBalance(acct3));

        // A subsequent withdraw operation should not withdraw anything now.
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        assertEquals(owings1, repo.getBalance(acct1));
        assertEquals(owings2, repo.getBalance(acct2));
        assertEquals(owings3, repo.getBalance(acct3));
    }

    @Test
    public void testOpenFundsLockContractNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        input = getLockInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsStartContractNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        input = getStartInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSstateContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testOpenFundsIsLockedAndIsLive() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        input = getIsLockedInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        input = getIsLiveInput(contract);
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testOpenFundsNoFundsWithdraw() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 10,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now try to withdraw.
        input = getWithdrawInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.TEN, repo.getBalance(acct));
    }

    @Test
    public void testOpenFundsDepositNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 10,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now try to deposit.
        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.TEN, repo.getBalance(acct));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct));
    }

    @Test
    public void testOpenFundsDepositForNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address other = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, true, 10,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now try to deposit.
        input = getDepositForInput(contract, other, BigInteger.ONE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.TEN, repo.getBalance(acct));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, other));
    }

    @Test
    public void testOpenFundsBulkDepositForNowDisabled() {
        Address owner = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        // Open all funds.
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(owner).execute(input, COST).getResultCode());

        // Now attempt to do a bulk deposit.
        int numBeneficiaries = 2;
        BigInteger deposit = new BigInteger("239785623");
        Address[] beneficiaries = new Address[numBeneficiaries];
        BigInteger[] amounts = new BigInteger[numBeneficiaries];
        for (int i = 0; i < numBeneficiaries; i++) {
            beneficiaries[i] = getNewExistentAccount(BigInteger.ZERO);
            amounts[i] = deposit;
        }

        AbstractTRS trs = newTRSuseContract(owner);
        BigInteger total = deposit.multiply(BigInteger.valueOf(numBeneficiaries));
        repo.addBalance(owner, total);
        input = getBulkDepositForInput(contract, beneficiaries, amounts);
        assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
        for (Address acc : beneficiaries) {
            assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acc));
        }
    }

    @Test
    public void testOpenFundsRefundNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address other = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 10,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(other).execute(input, COST).getResultCode());
        assertEquals(BigInteger.ONE, getDepositBalance(trs, contract, other));
        assertEquals(BigInteger.ZERO, repo.getBalance(other));

        input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now try to refund.
        input = getRefundInput(contract, other, BigInteger.ONE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.ONE, getDepositBalance(trs, contract, other));
        assertEquals(BigInteger.ZERO, repo.getBalance(other));
    }

    @Test
    public void testOpenFundsAddExtraNowDisabled() {
        Address acct = getNewExistentAccount(BigInteger.TEN);
        Address contract = createTRScontract(acct, false, true, 8,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSstateContract(acct);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(trs, contract));

        // Now try to add some extra funds.
        BigInteger extra = new BigInteger("23786523");
        repo.addBalance(acct, extra);
        input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.ZERO, getExtraFunds(trs, contract));
    }

    // <----------------------------------------HELPERS-------------------------------------------->

    /**
     * A rigorous checking that the bonus shares and total owings per the two accounts in a contract
     * make sense and satisfy all the expected properties. This method is intended to be used to
     * check very uneven distributions.
     *
     * @param deposit1 The first account's deposit amount.
     * @param deposit2 The second account's deposit amount.
     * @param bonus The bonus deposit.
     */
    private void checkOwingsGivenDepositsAndBonus(BigInteger deposit1, BigInteger deposit2, BigInteger bonus) {
        Address acct1 = getNewExistentAccount(deposit1);
        Address acct2 = getNewExistentAccount(deposit2);
        Address contract = createTRScontract(acct1, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, deposit1);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getDepositInput(contract, deposit2);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, acct1);

        // Verify acct1 and acct2 are not owed less than they deposited.
        AbstractTRS trs = newTRSstateContract(acct1);
        BigInteger acct1Owed = getTotalOwed(trs, contract, acct1);
        BigInteger acct2Owed = getTotalOwed(trs, contract, acct2);
        assertTrue(deposit1.compareTo(acct1Owed) <= 0);
        assertTrue(deposit2.compareTo(acct2Owed) <= 0);

        // Verify that the ratio of each account to the total deposits is very close to the corresponding
        // ratios of bonus tokens owed. The error here will be +/- 1 percent.
        BigInteger ratio1 = new BigDecimal(deposit1).divide(
            new BigDecimal(deposit1.add(deposit2)), 18, RoundingMode.HALF_DOWN).
            movePointRight(2).toBigInteger();
        BigInteger ratio2 = new BigDecimal(deposit2).divide(
            new BigDecimal(deposit1.add(deposit2)), 18, RoundingMode.HALF_DOWN).
            movePointRight(2).toBigInteger();
        BigInteger ratioSum = ratio1.add(ratio2);
        assertTrue(ratioSum.compareTo(new BigInteger("100")) <= 0);
        assertTrue(ratioSum.compareTo(new BigInteger("99")) >= 0);

        BigInteger share1 = getBonusShare(trs, contract, acct1);
        BigInteger share2 = getBonusShare(trs, contract, acct2);

        BigInteger expectedShare1Upper = new BigDecimal(bonus).multiply(new BigDecimal(ratio1.add(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare1Lower = new BigDecimal(bonus).multiply(new BigDecimal(ratio1.subtract(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare2Upper = new BigDecimal(bonus).multiply(new BigDecimal(ratio2.add(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare2Lower = new BigDecimal(bonus).multiply(new BigDecimal(ratio2.subtract(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        assertTrue(share1.compareTo(expectedShare1Upper) <= 0);
        assertTrue(share1.compareTo(expectedShare1Lower) >= 0);
        assertTrue(share2.compareTo(expectedShare2Upper) <= 0);
        assertTrue(share2.compareTo(expectedShare2Lower) >= 0);

        // Verify that the contract has enough funds to payout.
        BigInteger owedTotal = acct1Owed.add(acct2Owed);
        BigInteger fundsTotal = getTotalBalance(trs, contract).add(getBonusBalance(trs, contract));
        assertTrue(owedTotal.compareTo(fundsTotal) <= 0);
        BigInteger owedLowerBound = fundsTotal.subtract(BigInteger.ONE);
        assertTrue(owedTotal.compareTo(owedLowerBound) >= 0);
    }

    /**
     * A rigorous checking that the bonus shares and total owings per the three accounts in a contract
     * make sense and satisfy all the expected properties. The frac param is the fraction of the total
     * deposits that one of the depositors owns. This allows us to tamper with these relations.
     *
     * @param frac The fraction of the total deposits for one account, must be in range [0, 99]
     */
    private void checkOwings(int frac) {
        BigInteger deposit1 = BigInteger.TWO.pow(131);
        BigInteger deposit2 = BigInteger.TWO.pow(111).subtract(BigInteger.ONE);
        BigDecimal fraction = new BigDecimal(BigInteger.valueOf(frac)).movePointLeft(2);
        BigDecimal fracRemain = new BigDecimal(BigInteger.valueOf(100 - frac)).movePointLeft(2);
        BigDecimal depo1Dec = new BigDecimal(deposit1);
        BigDecimal depo2Dec = new BigDecimal(deposit2);

        BigDecimal multiplier = fraction.divide(fracRemain, 18, RoundingMode.HALF_DOWN);
        BigDecimal depositDec = multiplier.multiply(depo1Dec.add(depo2Dec));
        BigDecimal totalDec = depositDec.add(depo1Dec).add(depo2Dec);

        // Verify depositDec is indeed frac% of total.
        BigInteger fracBI = depositDec.divide(totalDec, 18, RoundingMode.HALF_DOWN).movePointRight(2).toBigInteger();
        assertEquals(fracBI, BigInteger.valueOf(frac));

        // Converted into integers we lose some precision; still very close to frac% though.
        BigInteger deposit = depositDec.toBigInteger();
        BigInteger total = deposit.add(deposit1).add(deposit2);

        BigInteger bonus = BigInteger.TWO.pow(56);

        // Actually perform the deposits and then do checks.
        Address acct = getNewExistentAccount(deposit);
        Address acct1 = getNewExistentAccount(deposit1);
        Address acct2 = getNewExistentAccount(deposit2);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, deposit);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getDepositInput(contract, deposit1);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getDepositInput(contract, deposit2);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, acct);

        // Verify the shares of the bonus tokens are very close to the expected amounts. That is,
        // their sum should be in the range [b-n-1, b] where b is total bonus tokens and n is number
        // of depositors.
        AbstractTRS trs = newTRSstateContract(acct);
        BigInteger lowerBound = bonus.subtract(BigInteger.TWO);
        BigInteger acctShare = getBonusShare(trs, contract, acct);
        BigInteger acct1Share = getBonusShare(trs, contract, acct1);
        BigInteger acct2Share = getBonusShare(trs, contract, acct2);
        BigInteger shareSum = acctShare.add(acct1Share).add(acct2Share);
        assertTrue(shareSum.compareTo(lowerBound) >= 0);
        assertTrue(shareSum.compareTo(bonus) <= 0);

        // Verify the total amount owed to each depositor is their deposit balance plus their bonus share.
        BigInteger owed = getTotalOwed(trs, contract, acct);
        BigInteger owed1 = getTotalOwed(trs, contract, acct1);
        BigInteger owed2 = getTotalOwed(trs, contract, acct2);
        assertEquals(owed, deposit.add(acctShare));
        assertEquals(owed1, deposit1.add(acct1Share));
        assertEquals(owed2, deposit2.add(acct2Share));

        // Verify the sum of the total owings falls in the range [T-n-1,T] for T equal to the total
        // contract balance plus the bonus balance.
        BigInteger totalOwing = owed.add(owed1).add(owed2);
        BigInteger upperBound = total.add(bonus);
        lowerBound = upperBound.subtract(BigInteger.TWO);
        assertTrue(totalOwing.compareTo(upperBound) <= 0);
        assertTrue(totalOwing.compareTo(lowerBound) >= 0);

        // Verify no depositors are owed less than they deposited.
        assertTrue(owed.compareTo(deposit) >= 0);
        assertTrue(owed1.compareTo(deposit1) >= 0);
        assertTrue(owed2.compareTo(deposit2) >= 0);

        // Verify the ratios are very close to expected. Error should be +/-1.
        BigInteger ratio = BigInteger.valueOf(frac);
        BigInteger ratio1 = new BigDecimal(deposit1).divide(new BigDecimal(total), 18, RoundingMode.HALF_DOWN).
            movePointRight(2).toBigInteger();
        BigInteger ratio2 = new BigDecimal(deposit2).divide(new BigDecimal(total), 18, RoundingMode.HALF_DOWN).
            movePointRight(2).toBigInteger();
        BigInteger ratioSum = ratio.add(ratio1).add(ratio2);
        assertTrue(ratioSum.compareTo(new BigInteger("100")) <= 0);
        assertTrue(ratioSum.compareTo(new BigInteger("99")) >= 0);

        BigInteger expectedShareUpper = new BigDecimal(bonus).multiply(new BigDecimal(ratio.add(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShareLower = new BigDecimal(bonus).multiply(new BigDecimal(ratio.subtract(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare1Upper = new BigDecimal(bonus).multiply(new BigDecimal(ratio1.add(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare1Lower = new BigDecimal(bonus).multiply(new BigDecimal(ratio1.subtract(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare2Upper = new BigDecimal(bonus).multiply(new BigDecimal(ratio2.add(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();
        BigInteger expectedShare2Lower = new BigDecimal(bonus).multiply(new BigDecimal(ratio2.subtract(BigInteger.ONE)).
            movePointLeft(2)).toBigInteger();

        assertTrue(acctShare.compareTo(expectedShareUpper) <= 0);
        assertTrue(acctShare.compareTo(expectedShareLower) >= 0);
        assertTrue(acct1Share.compareTo(expectedShare1Upper) <= 0);
        assertTrue(acct1Share.compareTo(expectedShare1Lower) >= 0);
        assertTrue(acct2Share.compareTo(expectedShare2Upper) <= 0);
        assertTrue(acct2Share.compareTo(expectedShare2Lower) >= 0);

        // Finally, verify that the sum of all owings is not greater than the sum of the total balance
        // plus bonus balance.
        BigInteger owingsTotal = owed.add(owed1).add(owed2);
        BigInteger fundsTotal = getTotalBalance(trs, contract).add(getBonusBalance(trs, contract));
        BigInteger fundsLowerBound = fundsTotal.subtract(BigInteger.TWO);
        assertTrue(owingsTotal.compareTo(fundsTotal) <= 0);
        assertTrue(owingsTotal.compareTo(fundsLowerBound) >= 0);
    }

}