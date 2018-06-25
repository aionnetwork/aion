package org.aion.precompiled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.TokenReleaseScheduleContract;
import org.aion.precompiled.contracts.TokenReleaseScheduleContract.PeriodUnit;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TokenReleaseScheduleContract class.
 */
public class TokenReleaseScheduleContractTest {
    private static final long COST = 21000L;    // should reflect the cost of the TRS
    private IRepositoryCache repo;

    @Before
    public void setup() {
        repo = new DummyRepo();
    }

    @After
    public void tearDown() {
        repo = null;
    }

    // Returns a new TRS that is called by caller.
    private TokenReleaseScheduleContract newTRS(Address caller) {
        return new TokenReleaseScheduleContract(repo, caller);
    }

    // Returns a newly created account.
    private Address newAccount() {
        return new Address(ECKeyFac.inst().create().getAddress());
    }

    // Returns a properly formatted input byte array for TRS create logic.
    private byte[] getCreateInput(BigInteger period, byte unit, byte percent, byte precision) {
        byte[] periodBytes = period.toByteArray();

        byte[] input = new byte[8];
        input[0] = (byte) 0x0;
        System.arraycopy(periodBytes, 0, input, 5 - periodBytes.length, periodBytes.length);
        input[5] = unit;
        input[6] = percent;
        input[7] = precision;
        return input;
    }

    // Returns a properly formatted byte array for TRS create logic with all bytes set to zero.
    private byte[] getCreateInput() {
        return getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x0, (byte) 0x0);
    }

    // <------------------------------------------------------------------------------------------->

    @Test
    public void testCreateNullInput() {
        ContractExecutionResult res = newTRS(newAccount()).execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateInsufficientNrg() {
        ContractExecutionResult res = newTRS(newAccount()).execute(getCreateInput(), COST - 1);
        assertEquals(ResultCode.OUT_OF_NRG, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateNrgTooHigh() {
        long nrg = StatefulPrecompiledContract.TX_NRG_MAX + 1;
        ContractExecutionResult res = newTRS(newAccount()).execute(getCreateInput(), nrg);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        ContractExecutionResult res = newTRS(newAccount()).execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateIncorrectInputSize() {
        // Test input too small.
        byte[] input = new byte[7];
        ContractExecutionResult res = newTRS(newAccount()).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test input too large.
        input = new byte[9];
        res = newTRS(newAccount()).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePeriodInSeconds() {
        // Test minimum period.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigInteger.ZERO, TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.SECONDS, TRS.tempUnit.get(contract));

        // Test maximum period.
        input = getCreateInput(new BigInteger("FFFFFFFF", 16), (byte) 0x0, (byte) 0x0, (byte) 0x0);
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigInteger(Arrays.copyOfRange(input, 1, 5)), TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.SECONDS, TRS.tempUnit.get(contract));
    }

    @Test
    public void testCreatePeriodInMinutes() {
        // Test minimum period.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x1, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigInteger.ZERO, TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.MINUTES, TRS.tempUnit.get(contract));

        // Test maximum period.
        input = getCreateInput(new BigInteger("FFFFFFFF", 16), (byte) 0x1, (byte) 0x0, (byte) 0x0);
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigInteger(Arrays.copyOfRange(input, 1, 5)), TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.MINUTES, TRS.tempUnit.get(contract));
    }

    @Test
    public void testCreatePeriodInHours() {
        // Test minimum period.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x2, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigInteger.ZERO, TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.HOURS, TRS.tempUnit.get(contract));

        // Test maximum period.
        input = getCreateInput(new BigInteger("FFFFFFFF", 16), (byte) 0x2, (byte) 0x0, (byte) 0x0);

        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigInteger(Arrays.copyOfRange(input, 1, 5)), TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.HOURS, TRS.tempUnit.get(contract));
    }

    @Test
    public void testCreatePeriodInDays() {
        // Test minimum period.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x3, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigInteger.ZERO, TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.DAYS, TRS.tempUnit.get(contract));

        // Test maximum period.
        input = getCreateInput(new BigInteger("FFFFFFFF", 16), (byte) 0x3, (byte) 0x0, (byte) 0x0);

        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigInteger(Arrays.copyOfRange(input, 1, 5)), TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.DAYS, TRS.tempUnit.get(contract));
    }

    @Test
    public void testCreatePeriodUnitInvalid() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x4, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePeriodIsUnsigned() {
        BigInteger periodBI = new BigInteger("1763214132");
        byte[] input = getCreateInput(periodBI.negate(), (byte) 0x0, (byte) 0x0, (byte) 0x0);

        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(periodBI, TRS.tempPeriod.get(contract));
        assertEquals(PeriodUnit.SECONDS, TRS.tempUnit.get(contract));
    }

    @Test
    public void testCreatePercentAsZero() {
        // Using no decimal places.
        byte[] input = getCreateInput();
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));

        // Using max decimal places.
        input[8] = (byte) 0xFF;
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));
    }

    // The max precision is 10^18 or 18 decimal places, the behaviour is that any precision value
    // that gives our percentage more than 18 decimal places will degrade into a string of 18 zeroes.
    @Test
    public void testCreatePrecisionAboveMax() {
        // Test on a single digit percentage.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x9, (byte) 0x13);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));

        // Test on a double digit percentage.
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x63, (byte) 0x14);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));

        // Test on a triple digit percentage (100).
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x64, (byte) 0x15);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));
    }

    @Test
    public void testCreatePrecisionAsMax() {
        // Test on a single digit percentage.
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x9, (byte) 0x12);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000009"), TRS.tempSpecMult.get(contract));

        // Test on a double digit percentage.
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x63, (byte) 0x14);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000009"), TRS.tempSpecMult.get(contract));

        // Test on a triple digit percentage (100).
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x64, (byte) 0x15);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000001"), TRS.tempSpecMult.get(contract));
    }

    @Test
    public void testCreatePrecisionBelowMax() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x9, (byte) 0x11);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000090"), TRS.tempSpecMult.get(contract));

        // Test on a double digit percentage.
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x63, (byte) 0x11);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000099"), TRS.tempSpecMult.get(contract));

        // Test on a triple digit percentage (100).
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x64, (byte) 0x12);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("0.000000000000000100"), TRS.tempSpecMult.get(contract));
    }

    @Test
    public void testCreatePercentAs100() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x64, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(new BigDecimal("100"), TRS.tempSpecMult.get(contract));
    }

    @Test
    public void testCreatePercentAbove100() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x65, (byte) 0x0);
        ContractExecutionResult res = newTRS(newAccount()).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreatePrecisionLargerThanPercent() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0xFF, (byte) 0x1A);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));

        // Test using a maximal precision.
        input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0xFF, (byte) 0xFF);
        TRS = newTRS(newAccount());
        res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        contract = new Address(res.getOutput());

        assertEquals(BigDecimal.ZERO, TRS.tempSpecMult.get(contract));
    }

    @Test
    public void testCreateOwner() {
        byte[] input = getCreateInput();
        Address caller = newAccount();
        TokenReleaseScheduleContract TRS = newTRS(caller);
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertEquals(caller, TRS.tempOwner.get(caller));
    }

    @Test
    public void testCreateNoAccountsInContract() {
        byte[] input = getCreateInput(BigInteger.ZERO, (byte) 0x0, (byte) 0x0, (byte) 0x0);
        TokenReleaseScheduleContract TRS = newTRS(newAccount());
        ContractExecutionResult res = TRS.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        Address contract = new Address(res.getOutput());

        assertTrue(TRS.tempState.get(contract).isEmpty());
    }

}
