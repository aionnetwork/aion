package org.aion.precompiled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.TRS.TRSownerContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSownerContract API.
 */
public class TRSownerContractTest {
    private Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    private long COST = 21000L;
    private IRepositoryCache repo;
    private List<Address> tempAddrs;

    @Before
    public void setup() {
        repo = new DummyRepo();
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

    // Returns a new TRSownerContract and calls the contract using caller.
    private TRSownerContract newTRSownerContract(Address caller) {
        return new TRSownerContract(repo, caller);
    }

    // Returns a new account with initial balance balance that exists in the repo.
    private Address getNewExistentAccount(BigInteger balance) {
        Address acct = Address.wrap(ECKeyFac.inst().create().getAddress());
        repo.createAccount(acct);
        repo.addBalance(acct, balance);
        repo.flush();
        tempAddrs.add(acct);
        return acct;
    }

    // Returns an input byte array for the create operation using the provided parameters.
    private byte[] getCreateInput(boolean isTest, boolean isDirectDeposit, int periods,
        BigInteger percent, int precision) {

        byte[] input = new byte[14];
        input[0] = (byte) 0x0;
        int depoAndTest = (isDirectDeposit) ? 1 : 0;
        depoAndTest += (isTest) ? 2 : 0;
        input[1] = (byte) depoAndTest;
        input[2] |= (periods >> Byte.SIZE);
        input[3] |= (periods & 0xFF);
        byte[] percentBytes = percent.toByteArray();
        if (percentBytes.length == 10) {
            System.arraycopy(percentBytes, 1, input, 4, 9);
        } else {
            System.arraycopy(percentBytes, 0, input, 14 - percentBytes.length - 1,
                percentBytes.length);
        }
        input[13] = (byte) precision;
        return input;
    }

    // Returns the address of the owner of the TRS contract given by the address contract.
    private Address fetchOwner(Address contract) {
        byte[] key1 = new byte[DataWord.BYTES];
        key1[0] = (byte) 0x80;
        DataWord value1 = (DataWord) repo.getStorageValue(contract, new DataWord(key1));

        byte[] key2 = new byte[DataWord.BYTES];
        key2[0] = (byte) 0x80;
        key2[DataWord.BYTES - 1] = (byte) 0x01;
        DataWord value2 = (DataWord) repo.getStorageValue(contract, new DataWord(key2));

        byte[] addr = new byte[Address.ADDRESS_LEN];
        System.arraycopy(value1.getData(), 0, addr, 0, DataWord.BYTES);
        System.arraycopy(value2.getData(), 0, addr, DataWord.BYTES, DataWord.BYTES);

        return Address.wrap(addr);
    }

    // Returns the periods configured for the TRS contract whose contract specs are given by specsData.
    private int fetchPeriods(DataWord specsData) {
        byte[] specs = specsData.getData();
        int periods = specs[12];
        periods <<= Byte.SIZE;
        periods |= (specs[13] & 0xFF);
        return periods;
    }

    // Returns true only if the RS contract whose contract specs are given by specsData is in Test Mode.
    private boolean fetchIsTest(DataWord specsData) {
        byte[] specs = specsData.getData();
        return specs[9] == 1;
    }

    // Returns true only if the RS contract whose contract specs are given by specsData has direct
    // deposit enabled.
    private boolean fetchIsDirectDeposit(DataWord specsData) {
        byte[] specs = specsData.getData();
        return specs[10] == 1;
    }

    // Returns the percentage configured for the TRS contract whose contract specs are given by specsData.
    private BigDecimal fetchPercentage(DataWord specsData) {
        byte[] specs = specsData.getData();
        BigInteger raw = new BigInteger(Arrays.copyOfRange(specs, 0, 9));
        return new BigDecimal(raw).movePointLeft((int) specs[11]);
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
        assertEquals(caller, fetchOwner(new Address(res.getOutput())));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateTestModeCorrectOwner() {
        byte[] input = getCreateInput(true, false, 1, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(AION, fetchOwner(new Address(res.getOutput())));
    }

    @Test
    public void testCreateVerifyPeriods() {
        // Test on min periods.
        int periods = 1;
        byte[] input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(periods, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(periods, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // Test on max periods.
        periods = 1200;
        input = getCreateInput(true, false, periods, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(periods, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));
    }

    @Test
    public void testCreateVerifyPeriodsByteBoundary() {
        // Tests periods that have their leading bits around the 8th bit where the 2 bytes separate.
        int periods = 0xFF;
        int periods2 = periods + 1;
        int periods3 = 0x1FF;

        byte[] input = getCreateInput(false, false, periods, BigInteger.ZERO, 0);
        TRSownerContract trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(periods, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        input = getCreateInput(false, false, periods2, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(periods2, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        input = getCreateInput(false, false, periods3, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(periods3, fetchPeriods((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(isTest, fetchIsTest((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // When true
        isTest = true;
        input = getCreateInput(isTest, false, 1, BigInteger.ZERO, 0);
        trs = newTRSownerContract(AION);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isTest, fetchIsTest((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(isDirectDeposit, fetchIsDirectDeposit((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // When true
        isDirectDeposit = true;
        input = getCreateInput(false, isDirectDeposit, 1, BigInteger.ZERO, 0);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(isDirectDeposit, fetchIsDirectDeposit((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(BigDecimal.ZERO, fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // Test 18 shifts.
        input = getCreateInput(false, false, 1, BigInteger.ZERO, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigDecimal.ZERO.movePointLeft(18), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(new BigDecimal(raw), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // Test 18 shifts.
        raw = new BigInteger("100000000000000000000");
        input = getCreateInput(false, false, 1, raw, 18);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(new BigDecimal(raw).movePointLeft(18), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // This is: 73.749309184932750917 with 18 shifts ... verify we get it back
        raw = new BigInteger("73749309184932750917");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // This is: 0.000000000000000001 with 18 shifts ... verify we get it back
        raw = new BigInteger("0000000000000000001");
        input = getCreateInput(false, false, 1, raw, 18);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(18), fetchPercentage((DataWord) repo.getStorageValue(
            Address.wrap(res.getOutput()), new DataWord(specsKey))));
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
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xC0;
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), fetchPercentage(
            (DataWord) repo.getStorageValue(Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // With 14 shifts we have: 8.72743562198734% -> good
        shifts = 14;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), fetchPercentage(
            (DataWord) repo.getStorageValue(Address.wrap(res.getOutput()), new DataWord(specsKey))));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // With 18 shifts we have: 000.000872743562198734% -> good
        shifts = 18;
        input = getCreateInput(false, false, 1, raw, shifts);
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(new BigDecimal(raw).movePointLeft(shifts), fetchPercentage(
            (DataWord) repo.getStorageValue(Address.wrap(res.getOutput()), new DataWord(specsKey))));
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

        // Different caller, should be different addr returned.
        trs = newTRSownerContract(getNewExistentAccount(BigInteger.ZERO));
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));

        // Same caller as original & nonce hasn't changed, should be same contract addr returned.
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(contract, Address.wrap(res.getOutput()));

        // Same caller as original but nonce changed, should be different addr returned.
        repo.incrementNonce(caller);
        repo.flush();
        trs = newTRSownerContract(caller);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertNotEquals(contract, Address.wrap(res.getOutput()));
        tempAddrs.add(Address.wrap(res.getOutput()));
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
    }

}