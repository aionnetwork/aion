package org.aion.precompiled.TRS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSqueryContract API.
 */
public class TRSqueryContractTest extends TRShelpers {
    private static final int MAX_OP = 5;

    @Before
    public void setup() {
        repo = new DummyRepo();
        ((DummyRepo) repo).storageErrorReturn = null;
        tempAddrs = new ArrayList<>();
        repo.addBalance(AION, BigInteger.ONE);
    }

    @After
    public void tearDown() {
        for (Address acct : tempAddrs) {
            repo.deleteAccount(acct);
        }
        tempAddrs = null;
        repo = null;
        senderKey = null;
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSqueryContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        ExecutionResult res;
        for (int i = 0; i <= MAX_OP; i++) {
            res = trs.execute(input, COST - 1);
            assertEquals(ResultCode.OUT_OF_NRG, res.getResultCode());
            assertEquals(0, res.getNrgLeft());
        }
    }

    @Test
    public void testTooMuchNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        ExecutionResult res;
        for (int i = 0; i <= MAX_OP; i++) {
            res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
            assertEquals(ResultCode.INVALID_NRG_LIMIT, res.getResultCode());
            assertEquals(0, res.getNrgLeft());
        }
    }

    @Test
    public void testInvalidOperation() {
        Address addr = getNewExistentAccount(BigInteger.ONE);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = new byte[DoubleDataWord.BYTES];
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            if ((i < 0) || (i > MAX_OP)) {
                input[0] = (byte) i;
                assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
            }
        }
    }

    // <-----------------------------------IS STARTED TRS TESTS------------------------------------>

    @Test
    public void testIsLiveInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLiveInput(acct);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLiveInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------------IS LOCKED TRS TESTS------------------------------------>

    @Test
    public void testIsLockedInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x1;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x1;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLockedInput(acct);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLockedInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------IS DIR DEPO ENABLED TRS TESTS-------------------------------->

    @Test
    public void testIsDepoEnabledInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x2;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x2;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsDirDepoEnabledInput(acct);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsDirDepoEnabledInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenDisabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenEnabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <--------------------------------------PERIOD TESTS----------------------------------------->

    @Test
    public void testPeriodInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x3;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x3;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodOfNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodInput(acct);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodBeforeIsLive() throws InterruptedException {
        Address contract = createAndLockTRScontract(AION, true, true, 4,
            BigInteger.ZERO, 0);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));

        byte[] input = getPeriodInput(contract);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodOnEmptyBlockchain() throws InterruptedException {
        // Then we are using the genesis block which has a timestamp of zero and therefore we are
        // in period 0 at this point.
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            4, BigInteger.ZERO, 0);
        createBlockchain(0, 0);

        byte[] input = getPeriodInput(contract);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodContractDeployedAfterBestBlock() throws InterruptedException {
        // Contract deployed 2 seconds after best block so period will be zero.
        createBlockchain(10, 0);
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            5, BigInteger.ZERO, 0);

        byte[] input = getPeriodInput(contract);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodWhenPeriodsIsOne() throws InterruptedException {
        // Contract deployed 2 seconds after best block so period will be zero.
        createBlockchain(3, 0);
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            1, BigInteger.ZERO, 0);

        byte[] input = getPeriodInput(contract);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // Now we should be in period 1 forever.
        addBlocks(2, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        addBlocks(3, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period3 = new BigInteger(res.getOutput()).intValue();

        assertEquals(0, period1);
        assertTrue(period2 > period1);
        assertEquals(1, period2);
        assertEquals(period3, period2);
    }

    @Test
    public void testPeriodAtDifferentMoments() throws InterruptedException {
        int numPeriods = 4;
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            numPeriods, BigInteger.ZERO, 0);
        createBlockchain(1, 0);

        // we expect the best block to have the same timestamp as the contract, and reasonable to
        // assume it is 1 or 2 seconds after at most. It should not be in period 4 yet.
        byte[] input = getPeriodInput(contract);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // We can safely assume now the best block is in period 4.
        addBlocks(2, TimeUnit.SECONDS.toMillis(2));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        addBlocks(5, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period3 = new BigInteger(res.getOutput()).intValue();

        assertTrue(period1 > 0);
        assertTrue(period2 > period1);
        assertEquals(period3, period2);
        assertEquals(numPeriods, period3);
    }

    // <-------------------------------------PERIOD AT TESTS--------------------------------------->

    @Test
    public void testPeriodAtInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x4;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x4;
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodAtInput(acct, 0);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtNegativeBlockNumber() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getPeriodAtInput(contract, -1);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtBlockTooLargeAndDoesNotExist() throws InterruptedException {
        int numBlocks = 1;

        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        createBlockchain(numBlocks, 0);

        byte[] input = getPeriodAtInput(contract, numBlocks + 1);
        ExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtBeforeIsLive() throws InterruptedException {
        int numBlocks = 5;

        Address contract = createAndLockTRScontract(AION, true, true, 4,
            BigInteger.ZERO, 0);
        createBlockchain(numBlocks, TimeUnit.SECONDS.toMillis(1));

        byte[] input = getPeriodAtInput(contract, numBlocks);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtAfterIsLive() throws InterruptedException {
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            4, BigInteger.ZERO, 0);
        createBlockchain(2, TimeUnit.SECONDS.toMillis(1));

        BigInteger expectedPeriod = getPeriodAt(newTRSstateContract(AION), contract, 1);
        byte[] input = getPeriodAtInput(contract, 1);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(expectedPeriod, new BigInteger(res.getOutput()));

        expectedPeriod = getPeriodAt(newTRSstateContract(AION), contract, 2);
        input = getPeriodAtInput(contract, 2);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(expectedPeriod, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtMaxPeriods() throws InterruptedException {
        int periods = 5;
        int numBlocks = 10;

        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            periods, BigInteger.ZERO, 0);
        createBlockchain(numBlocks, TimeUnit.SECONDS.toMillis(2));

        // Since we create a block every 2 seconds and a period lasts 1 seconds we should be more
        // than safe in assuming block number periods is in the last period.
        byte[] input = getPeriodAtInput(contract, periods);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.valueOf(periods), new BigInteger(res.getOutput()));

        input = getPeriodAtInput(contract, numBlocks);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.valueOf(periods), new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtWhenPeriodsIsOne() throws InterruptedException {
        int numBlocks = 5;

        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            1, BigInteger.ZERO, 0);
        createBlockchain(numBlocks, TimeUnit.SECONDS.toMillis(1));

        byte[] input = getPeriodAtInput(contract, 1);
        ExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));

        input = getPeriodAtInput(contract, numBlocks);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));
    }

    // <-------------------------AVAILABLE FOR WITHDRAWAL AT TRS TESTS----------------------------->

    @Test
    public void testAvailableForInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7,
            BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        byte[] shortInput = Arrays.copyOf(input, input.length - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSqueryContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testAvailableForInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7,
            BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSqueryContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testAvailableForContractNonExistent() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getAvailableForWithdrawalAtInput(acct, 0);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSqueryContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAvailableForContractNotYetLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7,
            BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSqueryContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAvailableForNoSpecial() {
        int numDepositors = 7;
        int periods = 59;
        BigInteger deposits = new BigInteger("3285423852334");
        BigInteger bonus = new BigInteger("32642352");
        BigDecimal percent = BigDecimal.ZERO;
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract) + (periods / 5);
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 12;
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
    }

    @Test
    public void testAvailableForWithSpecial() {
        int numDepositors = 9;
        int periods = 71;
        BigInteger deposits = new BigInteger("3245323");
        BigInteger bonus = new BigInteger("34645");
        BigDecimal percent = new BigDecimal("31.484645467");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract) + (periods / 3);
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 6;
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
    }

    @Test
    public void testAvailableForInFinalPeriod() {
        int numDepositors = 21;
        int periods = 92;
        BigInteger deposits = new BigInteger("237523675328");
        BigInteger bonus = new BigInteger("2356236434");
        BigDecimal percent = new BigDecimal("19.213343253242");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract) + periods;

        BigDecimal expectedFraction = new BigDecimal(1);
        expectedFraction = expectedFraction.setScale(18, RoundingMode.HALF_DOWN);

        byte[] input = getAvailableForWithdrawalAtInput(contract, timestamp);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            ExecutionResult res = newTRSqueryContract(acc).execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getResultCode());
            BigDecimal frac = new BigDecimal(new BigInteger(res.getOutput())).movePointLeft(18);
            assertEquals(expectedFraction, frac);
        }
    }

    @Test
    public void testAvailableForAtStartTime() {
        int numDepositors = 6;
        int periods = 8;
        BigInteger deposits = new BigInteger("2357862387523");
        BigInteger bonus = new BigInteger("35625365");
        BigDecimal percent = new BigDecimal("10.2353425");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract);
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
    }

    @Test
    public void testAvailableForTimePriorToStartTime() {
        int numDepositors = 16;
        int periods = 88;
        BigInteger deposits = new BigInteger("43634346");
        BigInteger bonus = new BigInteger("23532");
        BigDecimal percent = new BigDecimal("11");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract) - 1;

        BigDecimal expectedFraction = BigDecimal.ZERO;
        expectedFraction = expectedFraction.setScale(18, RoundingMode.HALF_DOWN);

        byte[] input = getAvailableForWithdrawalAtInput(contract, timestamp);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            ExecutionResult res = newTRSqueryContract(acc).execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getResultCode());
            BigDecimal frac = new BigDecimal(new BigInteger(res.getOutput())).movePointLeft(18);
            assertEquals(expectedFraction, frac);
        }
    }

    @Test
    public void testAvailableForMaxPeriods() {
        int numDepositors = 19;
        int periods = 1200;
        BigInteger deposits = new BigInteger("23869234762532654734875");
        BigInteger bonus = new BigInteger("23434634634");
        BigDecimal percent = new BigDecimal("6.1");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        long timestamp = trs.getTimestamp(contract) + (periods / 7);
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + (periods / 3);
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 1;
        checkAvailableForResults(trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
    }

}
