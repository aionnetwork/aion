package org.aion.precompiled.TRS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSownerContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSownerContract API.
 */
public class TRSownerContractTest extends TRShelpers {

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

    // Returns the address of the owner of the TRS contract given by the address contract.
    private Address getOwner(AbstractTRS trs, Address contract) {
        return trs.getContractOwner(contract);
    }

    // Returns the periods configured for the TRS contract.
    private int getPeriods(AbstractTRS trs, Address contract) {
        return AbstractTRS.getPeriods(trs.getContractSpecs(contract));
    }

    // Returns true only if the TRS contract is in Test Mode.
    private boolean isTestContract(AbstractTRS trs, Address contract) {
        return AbstractTRS.isTestContract(trs.getContractSpecs(contract));
    }

    // Returns true only if the TRS contract has direct deposit enabled.
    private boolean getIsDirectDepositEnabled(AbstractTRS trs, Address contract) {
        return AbstractTRS.isDirDepositsEnabled(trs.getContractSpecs(contract));
    }

    // Returns the percentage configured for the TRS contract.
    private BigDecimal getPercentage(AbstractTRS trs, Address contract) {
        return AbstractTRS.getPercentage(trs.getContractSpecs(contract));
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is locked.
    private boolean getIsContractLocked(IDataWord specsData) {
        return AbstractTRS.isContractLocked(specsData.getData());
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is live.
    private boolean getIsContractLive(IDataWord specsData) {
        return AbstractTRS.isContractLive(specsData.getData());
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSownerContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateInsufficientNrg() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST - 1);
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateTooMuchNrg() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
        assertEquals(ResultCode.INVALID_NRG_LIMIT, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    // <-------------------------------------CREATE TRS TESTS-------------------------------------->

    @Test
    public void testCreateTestModeNotAion() {
        // Test isDirectDeposit false
        byte[] input = getCreateInput(true, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test isDirectDeposit true
        input = getCreateInput(true, true, 1, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateZeroPeriods() {
        byte[] input = getCreateInput(false, false, 0, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePeriodsTooLarge() {
        // Smallest too-large period: 1201
        byte[] input = getCreateInput(false, false, 1201, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Largest too-large period: 65,535
        input = getCreateInput(false, false, 65_535, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePrecisionTooHigh() {
        // Smallest too-large precision: 19
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 19);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Largest too-large precision: 255
        input = getCreateInput(false, false, 1, BigInteger.ZERO, 255);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateCorrectOwner() {
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(caller, getOwner(trs, new Address(res.getOutput())));
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTestModeCorrectOwner() {
        byte[] input = getCreateInput(true, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        assertEquals(AION, getOwner(trs, new Address(res.getOutput())));
    }

    @Test
    public void testCreateVerifyPeriods() {
        // Test on min periods.
        int periods = 1;
        byte[] input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTestModeVerifyPeriods() {
        // Test on min periods.
        int periods = 1;
        byte[] input = getCreateInput(true, false, periods, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(AION);
        repo.flush();

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(true, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
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
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        input = getCreateInput(false, false, periods2, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods2, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        input = getCreateInput(false, false, periods3, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(periods3, getPeriods(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyIsTest() {
        // When false.
        boolean isTest = false;
        byte[] input = getCreateInput(isTest, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(isTest, isTestContract(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // When true
        isTest = true;
        input = getCreateInput(isTest, false, 1, BigInteger.ZERO, 0);
        trs = newTRSownerContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(isTest, isTestContract(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyIsDirectDeposit() {
        // When false
        boolean isDirectDeposit = false;
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, isDirectDeposit, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(isDirectDeposit, getIsDirectDepositEnabled(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // When true
        isDirectDeposit = true;
        input = getCreateInput(false, isDirectDeposit, 1, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(isDirectDeposit, getIsDirectDepositEnabled(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyZeroPercentage() {
        // Test no shifts.
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(BigDecimal.ZERO, getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test 18 shifts.
        input = getCreateInput(false, false, 1, BigInteger.ZERO, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(BigDecimal.ZERO.movePointLeft(18), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyOneHundredPercentage() {
        // Test no shifts.
        BigInteger raw = new BigInteger("100");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // Test 18 shifts.
        raw = new BigInteger("100000000000000000000");
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateAboveOneHundredPercentage() {
        // Test no shifts.
        BigInteger raw = new BigInteger("101");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test 18 shifts.
        raw = new BigInteger("100000000000000000001");
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test all 9 bytes of percent as 1's using zero shifts.
        byte[] rawBytes = new byte[]{ (byte) 0x0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        raw = new BigInteger(rawBytes);
        input = getCreateInput(false, false, 1, raw, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test all 9 bytes of percent as 1's using 18 shifts -> 4722.36% still no good.
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateVerify18DecimalPercentage() {
        // This is: 99.999999999999999999 with 18 shifts ... verify it is ok and we get it back
        BigInteger raw = new BigInteger("99999999999999999999");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, 18);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // This is: 73.749309184932750917 with 18 shifts ... verify we get it back
        raw = new BigInteger("73749309184932750917");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // This is: 0.000000000000000001 with 18 shifts ... verify we get it back
        raw = new BigInteger("0000000000000000001");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(18), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateValidAndInvalidPercentagesByShifting() {
        // With no shifting we have: 872743562198734% -> no good
        int shifts = 0;
        BigInteger raw = new BigInteger("872743562198734");
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, raw, shifts);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // With 12 shifts we have: 872.743562198734% -> no good
        shifts = 12;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // With 13 shifts we have: 87.2743562198734% -> good
        shifts = 13;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // With 14 shifts we have: 8.72743562198734% -> good
        shifts = 14;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));

        repo.incrementNonce(caller);
        repo.flush();

        // With 18 shifts we have: 000.000872743562198734% -> good
        shifts = 18;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), getPercentage(trs, Address.wrap(res.getOutput())));
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTRSaddressDeterministic() {
        // The TRS contract address returned must be deterministic, based on owner's addr + nonce.
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = Address.wrap(res.getOutput());
        tempAddrs.add(contract);

        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        // Different caller, should be different addr returned.
        trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));

        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        // Same caller as original & nonce hasn't changed, should be same contract addr returned.
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(contract, Address.wrap(res.getOutput()));

        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        // Same caller as original but nonce changed, should be different addr returned.
        repo.incrementNonce(caller);
        repo.flush();
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));

        word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
    }

    @Test
    public void testCreateSuccessNrgLeft() {
        long diff = 540;
        Address caller = getNewExistentAccount(BigInteger.ZERO);
        byte[] input = getCreateInput(false, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(caller);
        ContractExecutionResult res = trs.execute(input, COST + diff);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(diff, res.getNrgLeft());
        tempAddrs.add(Address.wrap(res.getOutput()));

        IDataWord word = repo.getStorageValue(Address.wrap(res.getOutput()), getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));
    }

    @Test
    public void testAbleToNullifyWhileUnlocked() {
        //TODO
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
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on address size 31.
        input = new byte[32];
        input[0] = (byte) 0x1;
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockNotTRScontractAddress() {
        // Test attempt to lock a regular account.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(acct);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test attempt to lock what looks like a TRS address (proper prefix).
        input = getLockInput(contract);
        input[input.length - 1] = (byte) ~input[input.length - 1];
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockNotContractOwner() {
        // Test not in test mode: owner is acct, caller is AION.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode: owner is AION, caller is acct.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);
        input = getLockInput(contract);
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockButContractAlreadyLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        byte[] input = getLockInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));    // lock worked.
        assertFalse(getIsContractLive(word));

        // attempt to lock the contract again...
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        input = getLockInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));    // lock worked.
        assertFalse(getIsContractLive(word));

        // attempt to lock the contract again...
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testLockAndVerifyIsLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        byte[] input = getLockInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));    // lock worked.
        assertFalse(getIsContractLive(word));

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        input = getLockInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));    // lock worked.
        assertFalse(getIsContractLive(word));
    }

    @Test
    public void testLockButContractIsLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getStartInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLive(word));

        // Attempt to lock live contract.
        input = getLockInput(contract);
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        input = getStartInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLive(word));

        // Attempt to lock live contract.
        input = getLockInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCannotRefundOnceLocked() {
        //TODO: in use contract
    }

    @Test
    public void testBonusDepositsOnceLocked() {
        //TODO: in use contract
    }

    @Test
    public void testBonusDepositsBeforeLocked() {
        //TODO: in use contract
    }

    @Test
    public void testAbleToNullifyWhileLocked() {
        //TODO
    }

    // <-------------------------------------START TRS TESTS--------------------------------------->

    @Test
    public void testStartAddressTooSmall() {
        // Test on empty address.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        createAndLockTRScontract(acct, false, false, 1, BigInteger.ZERO,
            0);
        byte[] input = new byte[1];
        input[0] = (byte) 0x1;
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on address size 31.
        input = new byte[32];
        input[0] = (byte) 0x1;
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartAddressTooLarge() {
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false,
            1, BigInteger.ZERO, 0);
        byte[] input = new byte[34];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

    }

    @Test
    public void testStartNotTRScontractAddress() {
        // Test regular address as a TRS address.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false,
            1, BigInteger.ZERO, 0);
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        repo.incrementNonce(acct);
        repo.flush();

        // Test what looks like a proper TRS address (has TRS prefix).
        input[input.length - 1] = (byte) ~input[input.length - 1];  // flip a bit
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartNotContractOwner() {
        // Test not in test mode: owner is acct, caller is AION.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getLockInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode: owner is AION, caller is acct.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);
        input = getLockInput(contract);
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartButContractAlreadyLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getStartInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertTrue(getIsContractLive(word));  // contract is live now.

        // Try to start contract again.
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        input = getStartInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertTrue(getIsContractLive(word));  // contract is live now.

        // Try to start contract again.
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartButContractNotLocked() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));

        byte[] input = getStartInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        contract = createTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        word = repo.getStorageValue(contract, getSpecKey());
        assertFalse(getIsContractLocked(word));

        input = getStartInput(contract);
        trs = new TRSownerContract(repo, acct);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testStartAndVerifyIsLive() {
        // Test not in test mode.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        IDataWord word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        byte[] input = getStartInput(contract);
        TRSownerContract trs = new TRSownerContract(repo, acct);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertTrue(getIsContractLive(word));  // true now.

        // Test in test mode.
        contract = createAndLockTRScontract(AION, true, false, 1,
            BigInteger.ZERO, 0);

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertFalse(getIsContractLive(word));

        input = getStartInput(contract);
        trs = new TRSownerContract(repo, AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        word = repo.getStorageValue(contract, getSpecKey());
        assertTrue(getIsContractLocked(word));
        assertTrue(getIsContractLive(word));  // true now.
    }

    @Test
    public void testCannotRefundWhileLive() {
        //TODO: in use contract
    }

    @Test
    public void testCannotMakeBonusDepositsWhileLive() {
        //TODO: in use contract
    }

    @Test
    public void testNotAbleToNullifyWhileLive() {
        //TODO
    }

}