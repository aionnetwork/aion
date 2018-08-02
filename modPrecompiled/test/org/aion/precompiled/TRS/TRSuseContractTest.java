package org.aion.precompiled.TRS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSuseContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSuseContract API.
 */
public class TRSuseContractTest extends TRShelpers {
    private static final int MAX_OP = 6;

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
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSuseContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        ExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSuseContract trs = newTRSuseContract(addr);
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
        TRSuseContract trs = newTRSuseContract(addr);
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
        TRSuseContract trs = newTRSuseContract(addr);
        byte[] input = new byte[DoubleDataWord.BYTES];
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            if ((i < 0) || (i > MAX_OP)) {
                input[0] = (byte) i;
                assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
            }
        }
    }

    // <-------------------------------------DEPOSIT TRS TESTS------------------------------------->

    @Test
    public void testDepositInputTooShort() {
        // Test on minimum too-small amount.
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        byte[] input = new byte[1];
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-small amount.
        input = new byte[160];
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositInputTooLong() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        byte[] input = new byte[162];
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositToNonExistentContract() {
        // Test on contract address actually an account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(acct, BigInteger.TWO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on contract address looks like a legit TRS address (proper prefix).
        byte[] addr = new byte[Address.ADDRESS_LEN];
        System.arraycopy(acct.toBytes(), 0, addr, 0, Address.ADDRESS_LEN);
        addr[0] = (byte) 0xC0;

        input = getDepositInput(Address.wrap(addr), BigInteger.TWO);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositInsufficientBalance() {
        // Test not in test mode.
        // Test on minimum too-large amount.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE.add(BigInteger.ONE));
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-large amount.
        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        // Test on minimum too-large amount.
        contract = createTRScontract(AION, true, true, 1, BigInteger.ZERO, 0);
        input = getDepositInput(contract, DEFAULT_BALANCE.add(BigInteger.ONE));
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-large amount.
        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositDirectDepositsDisabledCallerIsOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, false, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.TWO, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositDirectDepositsDisabledCallerNotOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address owner = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, false, 1, BigInteger.ZERO, 0);

        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositCallerIsContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1, BigInteger.ZERO, 0);

        TRSuseContract trs = newTRSuseContract(contract);
        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositZero() {
        // Test zero deposit with zero balance.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ZERO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));

        // Test zero deposit with non-zero balance.
        acct = getNewExistentAccount(DEFAULT_BALANCE);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getDepositInput(contract, BigInteger.ZERO);
       res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositOne() {
        // Test deposit with one balance.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(BigInteger.ONE, getDepositBalance(trs, contract, acct));
        assertEquals(BigInteger.ONE, getTotalBalance(trs, contract));

        // Test deposit with balance larger than one.
        acct = getNewExistentAccount(DEFAULT_BALANCE);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getDepositInput(contract, BigInteger.ONE);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(DEFAULT_BALANCE.subtract(BigInteger.ONE), repo.getBalance(acct));
        assertEquals(BigInteger.ONE, getDepositBalance(trs, contract, acct));
        assertEquals(BigInteger.ONE, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositFullBalance() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));

        // Test on max deposit amount.
        BigInteger max = getMaxOneTimeDeposit();
        acct = getNewExistentAccount(max);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(max, getDepositBalance(trs, contract, acct));
        assertEquals(max, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositMaxOneTimeAmount() {
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(max.add(DEFAULT_BALANCE));
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getMaxDepositInput(contract);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct));
        assertEquals(max, getDepositBalance(trs, contract, acct));
        assertEquals(max, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositMultipleTimes() {
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(max);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        BigInteger amt = new BigInteger("123456");
        BigInteger left = max;
        BigInteger depo = BigInteger.ZERO;
        byte[] input = getDepositInput(contract, amt);
        for (int i = 0; i < 7; i++) {
            ExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getResultCode());
            assertEquals(0, res.getNrgLeft());
            left = left.subtract(amt);
            depo = depo.add(amt);
        }

        assertEquals(left, repo.getBalance(acct));
        assertEquals(depo, getDepositBalance(trs, contract, acct));
        assertEquals(depo, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositMaxMultipleTimes() {
        // We do a large number of max deposits. There is no way we can test hitting the absolute
        // maximum but we can still hit it a good number of times.
        BigInteger maxTotal = getMaxTotalDeposit();
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(maxTotal);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        BigInteger left = maxTotal;
        BigInteger depo = BigInteger.ZERO;
        byte[] input = getMaxDepositInput(contract);
        for (int i = 0; i < 100; i++) {
            ExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getResultCode());
            assertEquals(0, res.getNrgLeft());
            left = left.subtract(max);
            depo = depo.add(max);
        }

        assertEquals(left, repo.getBalance(acct));
        assertEquals(depo, getDepositBalance(trs, contract, acct));
        assertEquals(depo, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositMultipleDepositors() {
        BigInteger max = getMaxOneTimeDeposit();
        Address acct1 = getNewExistentAccount(max);
        Address acct2 = getNewExistentAccount(max);
        Address acct3 = getNewExistentAccount(max);
        BigInteger acct1Bal = max;
        BigInteger acct2Bal = max;
        BigInteger acct3Bal = max;
        BigInteger acct1Depo = BigInteger.ZERO;
        BigInteger acct2Depo = BigInteger.ZERO;
        BigInteger acct3Depo = BigInteger.ZERO;

        Address contract = createTRScontract(acct1, false, true, 1, BigInteger.ZERO, 0);
        BigInteger amt1 = new BigInteger("123456");
        BigInteger amt2 = new BigInteger("4363123");
        BigInteger amt3 = new BigInteger("8597455434");

        byte[] input1 = getDepositInput(contract, amt1);
        byte[] input2 = getDepositInput(contract, amt2);
        byte[] input3 = getDepositInput(contract, amt3);

        for (int i = 0; i < 10; i++) {
            newTRSuseContract(acct1).execute(input1, COST);
            newTRSuseContract(acct2).execute(input2, COST);
            newTRSuseContract(acct3).execute(input3, COST);
            newTRSuseContract(acct1).execute(input3, COST);

            acct1Bal = acct1Bal.subtract(amt1).subtract(amt3);
            acct1Depo = acct1Depo.add(amt1).add(amt3);
            acct2Bal = acct2Bal.subtract(amt2);
            acct2Depo = acct2Depo.add(amt2);
            acct3Bal = acct3Bal.subtract(amt3);
            acct3Depo = acct3Depo.add(amt3);
        }

        assertEquals(acct1Bal, repo.getBalance(acct1));
        assertEquals(acct2Bal, repo.getBalance(acct2));
        assertEquals(acct3Bal, repo.getBalance(acct3));

        TRSuseContract trs = newTRSuseContract(acct1);
        assertEquals(acct1Depo, getDepositBalance(trs, contract, acct1));
        assertEquals(acct2Depo, getDepositBalance(trs, contract, acct2));
        assertEquals(acct3Depo, getDepositBalance(trs, contract, acct3));
        assertEquals(acct1Depo.add(acct2Depo).add(acct3Depo), getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositThatCausesOverflow() {
        // First we deposit 2**31 - 1 and then deposit 1 to overflow into next row.
        BigInteger total = BigInteger.TWO.pow(255);
        Address acct = getNewExistentAccount(total);
        BigInteger amount = total.subtract(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, amount);
        trs.execute(input, COST);

        input = getDepositInput(contract, BigInteger.ONE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(total, getDepositBalance(trs, contract, acct));
        assertEquals(total, getTotalBalance(trs, contract));

        // Second we deposit 1 and then deposit 2**31 - 1 to overflow into next row.
        acct = getNewExistentAccount(total);
        amount = total.subtract(BigInteger.ONE);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        trs = newTRSuseContract(acct);
        input = getDepositInput(contract, BigInteger.ONE);
        trs.execute(input, COST);

        input = getDepositInput(contract, amount);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(total, getDepositBalance(trs, contract, acct));
        assertEquals(total, getTotalBalance(trs, contract));
    }

    @Test
    public void testDepositNumRowsWhenAllRowsFull() {
        BigInteger total = BigInteger.TWO.pow(255);
        Address acct = getNewExistentAccount(total);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, total);
        trs.execute(input, COST);

        int rows = repo.getStorageValue(contract, newIDataWord(acct.toBytes())).getData()[0] & 0x0F;
        assertEquals(1, rows);
    }

    @Test
    public void testDepositNumRowsWhenOneRowHasOneNonZeroByte() {
        BigInteger total = BigInteger.TWO.pow(256);
        Address acct = getNewExistentAccount(total);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, total);
        trs.execute(input, COST);

        int rows = repo.getStorageValue(contract, newIDataWord(acct.toBytes())).getData()[0] & 0x0F;
        assertEquals(2, rows);
    }

    @Test
    public void testDepositWhileTRSisLocked() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositWhileTRSisLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testAccountIsValidPriorToDeposit() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        assertFalse(accountIsValid(trs, contract, acct));
    }

    @Test
    public void testAccountIsValidAfterDeposit() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        trs.execute(input, COST);
        assertTrue(accountIsValid(trs, contract, acct));
    }

    @Test
    public void testAccountIsValidAfterMultipleDeposits() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        trs.execute(input, COST);
        trs.execute(input, COST);
        trs.execute(input, COST);
        trs.execute(input, COST);
        assertTrue(accountIsValid(trs, contract, acct));
    }

    @Test
    public void testMultipleAccountsValidAfterDeposits() {
        Address acct1 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct1, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, BigInteger.ONE);

        TRSuseContract trs1 = newTRSuseContract(acct1);
        trs1.execute(input, COST);
        TRSuseContract trs2 = newTRSuseContract(acct2);
        trs2.execute(input, COST);
        TRSuseContract trs3 = newTRSuseContract(acct3);
        trs3.execute(input, COST);

        assertTrue(accountIsValid(trs1, contract, acct1));
        assertTrue(accountIsValid(trs2, contract, acct2));
        assertTrue(accountIsValid(trs3, contract, acct3));
    }

    /*
     * We have an account "come" (deposit) and then "go" (refund all) and then come back again.
     * We want to ensure that the account's is-valid bit, its balance and the linked list are
     * all responding as expected to this.
     * First we test when we have only 1 user and then with multiple users.
    */

    @Test
    public void testAccountComingAndGoingSolo() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Come.
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        trs.execute(input, COST);

        assertTrue(accountIsValid(trs, contract, acct));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct));
        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListNext(trs, contract, acct));

        // Go.
        input = getRefundInput(contract, acct, DEFAULT_BALANCE);
        trs.execute(input, COST);

        assertFalse(accountIsValid(trs, contract, acct));
        assertFalse(accountIsEligibleForSpecial(trs, contract, acct));
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct));
        assertNull(getLinkedListHead(trs, contract));
        assertNull(getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListNext(trs, contract, acct));

        // Come back.
        BigInteger amt =  DEFAULT_BALANCE.subtract(BigInteger.ONE);
        input = getDepositInput(contract, amt);
        trs.execute(input, COST);

        assertTrue(accountIsValid(trs, contract, acct));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct));
        assertEquals(amt, getTotalBalance(trs, contract));
        assertEquals(amt, getDepositBalance(trs, contract, acct));
        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListNext(trs, contract, acct));
    }

    @Test
    public void testAccountComingAndGoingMultipleUsers() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acct1 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct4 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(owner);

        // Come.    We have:    head-> acct4 <-> acct3 <-> acct2 <-> acct1 -> null
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        newTRSuseContract(acct1).execute(input, COST);
        newTRSuseContract(acct2).execute(input, COST);
        newTRSuseContract(acct3).execute(input, COST);
        newTRSuseContract(acct4).execute(input, COST);

        assertTrue(accountIsValid(trs, contract, acct1));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct1));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct1));
        assertTrue(accountIsValid(trs, contract, acct2));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertTrue(accountIsValid(trs, contract, acct3));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct3));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct3));
        assertTrue(accountIsValid(trs, contract, acct4));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct4));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct4));
        assertEquals(DEFAULT_BALANCE.multiply(new BigInteger("4")), getTotalBalance(trs, contract));
        assertEquals(acct4, getLinkedListHead(trs, contract));
        assertEquals(acct3, getLinkedListNext(trs, contract, acct4));
        assertEquals(acct2, getLinkedListNext(trs, contract, acct3));
        assertEquals(acct1, getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListNext(trs, contract, acct1));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct1));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct2));
        assertEquals(acct4, getLinkedListPrev(trs, contract, acct3));
        assertNull(getLinkedListPrev(trs, contract, acct4));

        // Go.  We have:    head-> acct3 <-> acct1 -> null
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        trs.execute(input, COST);
        input = getRefundInput(contract, acct4, DEFAULT_BALANCE);
        trs.execute(input, COST);

        assertTrue(accountIsValid(trs, contract, acct1));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct1));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct1));
        assertFalse(accountIsValid(trs, contract, acct2));
        assertFalse(accountIsEligibleForSpecial(trs, contract, acct2));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct2));
        assertTrue(accountIsValid(trs, contract, acct3));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct3));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct3));
        assertFalse(accountIsValid(trs, contract, acct4));
        assertFalse(accountIsEligibleForSpecial(trs, contract, acct4));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct4));
        assertEquals(DEFAULT_BALANCE.multiply(BigInteger.TWO), getTotalBalance(trs, contract));
        assertEquals(acct3, getLinkedListHead(trs, contract));
        assertEquals(acct1, getLinkedListNext(trs, contract, acct3));
        assertNull(getLinkedListNext(trs, contract, acct1));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct1));
        assertNull(getLinkedListPrev(trs, contract, acct3));

        // Come back. We have:  head-> acct2 <-> acct4 <-> acct3 <-> acct1 -> null
        input = getDepositInput(contract, DEFAULT_BALANCE);
        newTRSuseContract(acct4).execute(input, COST);
        newTRSuseContract(acct2).execute(input, COST);

        assertTrue(accountIsValid(trs, contract, acct1));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct1));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct1));
        assertTrue(accountIsValid(trs, contract, acct2));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertTrue(accountIsValid(trs, contract, acct3));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct3));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct3));
        assertTrue(accountIsValid(trs, contract, acct4));
        assertTrue(accountIsEligibleForSpecial(trs, contract, acct4));
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct4));
        assertEquals(DEFAULT_BALANCE.multiply(new BigInteger("4")), getTotalBalance(trs, contract));
        assertEquals(acct2, getLinkedListHead(trs, contract));
        assertEquals(acct4, getLinkedListNext(trs, contract, acct2));
        assertEquals(acct3, getLinkedListNext(trs, contract, acct4));
        assertEquals(acct1, getLinkedListNext(trs, contract, acct3));
        assertNull(getLinkedListNext(trs, contract, acct1));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct1));
        assertEquals(acct4, getLinkedListPrev(trs, contract, acct3));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct4));
        assertNull(getLinkedListPrev(trs, contract, acct2));
    }

    // <----------------------------------TRS WITHDRAWAL TESTS------------------------------------->

    @Test
    public void testWithdrawInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[32];
        input[0] = 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN - 1);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testWithdrawInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[34];
        input[0] = 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testWithdrawContractNotLockedOrLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        input = getWithdrawInput(contract);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(DEFAULT_BALANCE, getDepositBalance(newTRSuseContract(acct), contract, acct));
    }

    @Test
    public void testWithdrawContractLockedNotLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        // Lock the contract.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        input = getWithdrawInput(contract);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(DEFAULT_BALANCE, getDepositBalance(newTRSuseContract(acct), contract, acct));
    }

    @Test
    public void testLastWithdrawalPeriodNonExistentContract() {
        Address account = getNewExistentAccount(BigInteger.ONE);
        assertEquals(-1, getAccountLastWithdrawalPeriod(newTRSuseContract(account), account, account));
    }

    @Test
    public void testLastWithdrawalPeriodAccountNotInContract() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address stranger = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        assertEquals(-1, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, stranger));
    }

    @Test
    public void testLastWithdrawalPeriodBeforeLive() {
        // Test before locking.
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acc = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        // Test that locking changes nothing.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(owner).execute(input, COST).getResultCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));
    }

    @Test
    public void testLastWithdrawalPeriodOnceLive() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acc = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());

        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(owner).execute(input, COST).getResultCode());
        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(owner).execute(input, COST).getResultCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));
    }

    @Test
    public void testLastWithdrawalPeriodComingAndGoing() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acc = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        input = getRefundInput(contract, acc, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(owner).execute(input, COST).getResultCode());
        assertEquals(-1, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));
    }

    @Test
    public void testWithdrawMultipleTimesSamePeriod() throws InterruptedException {
        int periods = DEFAULT_BALANCE.intValue();
        repo.addBalance(AION, DEFAULT_BALANCE);
        BigInteger initBal = repo.getBalance(AION);
        Address contract = createTRScontract(AION, true, true, periods,
            BigInteger.ZERO, 0);

        BigInteger expectedBalAfterDepo = initBal.subtract(DEFAULT_BALANCE);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        assertEquals(expectedBalAfterDepo, repo.getBalance(AION));
        lockAndStartContract(contract, AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));

        input = getWithdrawInput(contract);
        TRSuseContract trs = newTRSuseContract(AION);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, DEFAULT_BALANCE,
            DEFAULT_BALANCE, BigInteger.ZERO, BigDecimal.ZERO, periods);
        BigInteger expectedBal = expectedBalAfterDepo.add(expectedAmt);

        // Try to keep withdrawing...
        for (int i = 0; i < 5; i++) {
            assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
            assertEquals(expectedBal, repo.getBalance(AION));
        }
    }

    @Test
    public void testWithdrawNoBonusFunds() throws InterruptedException {
        BigDecimal percent = new BigDecimal("10");
        BigInteger deposits = DEFAULT_BALANCE;
        BigInteger bonus = BigInteger.ZERO;
        int periods = 3;
        int numDepositors = 4;
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        // We try to put the contract in a non-final period and withdraw.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        AbstractTRS trs = newTRSstateContract(AION);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> depositors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : depositors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }

        // Now put the contract in its final period and withdraw. All accounts should have their
        // origial balance back.
        addBlocks(1, TimeUnit.SECONDS.toMillis(3));
        assertEquals(periods, getContractCurrentPeriod(trs, contract));
        for (Address acc : depositors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(deposits, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSpecialOneOffOnFirstWithdraw() throws InterruptedException {
        BigDecimal percent = new BigDecimal("8");
        BigInteger deposits = new BigInteger("9283653985313");
        BigInteger bonus = new BigInteger("2386564");
        int periods = 3;
        int depositors = 5;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in a non-final period and withdraw.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        AbstractTRS trs = newTRSstateContract(AION);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSpecialEventBeforePeriodOne() throws InterruptedException {
        BigDecimal percent = new BigDecimal("72");
        BigInteger deposits = new BigInteger("7656234");
        BigInteger bonus = new BigInteger("92834756532");
        int periods = 2;
        int depositors = 3;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Only genesis block exists so contract is live but the first period hasn't begun because
        // there is no block that has been made after the contract went live. All depositors will
        // be eligible to withdraw only the special event amount.
        createBlockchain(0, 0);
        AbstractTRS trs = newTRSstateContract(AION);
        BigInteger expectedAmt = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total),
            new BigDecimal(bonus), percent);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }

        // Let's try and withdraw again, now we should not be able to.
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSomePeriodsBehind() throws InterruptedException {
        BigDecimal percent = new BigDecimal("25");
        BigInteger deposits = new BigInteger("384276532");
        BigInteger bonus = new BigInteger("9278");
        int periods = 7;
        int depositors = 5;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in a non-final period greater than period 1 and withdraw.
        createBlockchain(3, TimeUnit.SECONDS.toMillis(1));
        AbstractTRS trs = newTRSstateContract(AION);
        assertTrue(getCurrentPeriod(trs, contract).compareTo(BigInteger.ONE) > 0);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawMultipleTimesInFinalPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("41.234856");
        BigInteger deposits = new BigInteger("394876");
        BigInteger bonus = new BigInteger("329487682345");
        int periods = 3;
        int depositors = 5;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in a final period and withdraw.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(4));
        AbstractTRS trs = newTRSstateContract(AION);
        assertEquals(BigInteger.valueOf(periods), getCurrentPeriod(trs, contract));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Try to withdraw again from the final period.
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawOncePerBlockTotalOwings() throws InterruptedException {
        BigDecimal percent = new BigDecimal("0.000001");
        BigInteger deposits = new BigInteger("384276532");
        BigInteger bonus = new BigInteger("922355678");
        int periods = 4;
        int depositors = 8;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(0, 0);

        boolean isAccNotDone;
        boolean isDone = false;
        byte[] input = getWithdrawInput(contract);
        while (!isDone) {
            isAccNotDone = false;

            Set<Address> contributors = getAllDepositors(trs, contract);
            for (Address acc : contributors) {
                if (newTRSuseContract(acc).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                    isAccNotDone = true;
                }
            }

            addBlocks(1, TimeUnit.SECONDS.toMillis(1));
            if (!isAccNotDone) { isDone = true; }
        }

        // Each account should have its total owings by now.
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        BigInteger sum = BigInteger.ZERO;
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
            sum = sum.add(owings);
        }

        // Ensure the contract does not pay out more than it has and that the lower bound on the remainder
        // is n-1 for n depositors.
        BigInteger totalFunds = total.add(bonus);
        assertTrue(sum.compareTo(totalFunds) <= 0);
        assertTrue(sum.compareTo(totalFunds.subtract(BigInteger.valueOf(depositors - 1))) >= 0);
    }

    @Test
    public void testWithdrawLastPeriodOnlyTotalOwings() throws InterruptedException {
        BigDecimal percent = new BigDecimal("13.522");
        BigInteger deposits = new BigInteger("384276436375532");
        BigInteger bonus = new BigInteger("92783242");
        int periods = 3;
        int depositors = 2;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in the final period and withdraw.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(4));
        AbstractTRS trs = newTRSstateContract(AION);

        // We are in last period so we expect to withdraw our total owings.
        BigInteger currPeriod = getCurrentPeriod(trs, contract);
        assertEquals(BigInteger.valueOf(periods), currPeriod);
        BigInteger accOwed = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(accOwed, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSomePeriodsTotalOwings() throws InterruptedException {
        BigDecimal percent = new BigDecimal("13.33333112");
        BigInteger deposits = new BigInteger("3842762");
        BigInteger bonus = new BigInteger("9223247118");
        int periods = 5;
        int depositors = 4;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(0, 0);

        boolean isAccNotDone;
        boolean isDone = false;
        while (!isDone) {
            isAccNotDone = false;

            Set<Address> contributors = getAllDepositors(trs, contract);
            byte[] input = getWithdrawInput(contract);
            for (Address acc : contributors) {
                if (newTRSuseContract(acc).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                    isAccNotDone = true;
                }
            }

            addBlocks(1, TimeUnit.SECONDS.toMillis(2));
            if (!isAccNotDone) { isDone = true; }
        }

        // Each account should have its total owings by now.
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        BigInteger sum = BigInteger.ZERO;
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
            sum = sum.add(owings);
        }

        // Ensure the contract does not pay out more than it has and that the lower bound on the remainder
        // is n-1 for n depositors.
        BigInteger totalFunds = total.add(bonus);
        assertTrue(sum.compareTo(totalFunds) <= 0);
        assertTrue(sum.compareTo(totalFunds.subtract(BigInteger.valueOf(depositors - 1))) >= 0);
    }

    @Test
    public void testWithdrawLargeWealthGap() throws InterruptedException {
        BigInteger bal1 = BigInteger.ONE;
        BigInteger bal2 = new BigInteger("968523984325");
        BigInteger bal3 = new BigInteger("129387461289371");
        Address acc1 = getNewExistentAccount(bal1);
        Address acc2 = getNewExistentAccount(bal2);
        Address acc3 = getNewExistentAccount(bal3);
        Address contract = createTRScontract(AION, true, true, 4,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, bal1);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc1).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal2);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc2).execute(input, COST).getResultCode());
        input = getDepositInput(contract, bal3);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc3).execute(input, COST).getResultCode());
        BigInteger bonus = new BigInteger("9238436745867623");
        repo.addBalance(contract, bonus);
        lockAndStartContract(contract, AION);

        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(0, 0);

        boolean isDone = false;
        input = getWithdrawInput(contract);
        while (!isDone) {

            Set<Address> contributors = getAllDepositors(trs, contract);
            for (Address acc : contributors) {
                newTRSuseContract(acc).execute(input, COST);
            }

            if (getCurrentPeriod(trs, contract).intValue() == 4) { isDone = true; }
            addBlocks(1, TimeUnit.SECONDS.toMillis(1));
        }

        // We should have more than 1 owings back because bonus is large enough.
        BigInteger total = bal1.add(bal2.add(bal3));
        BigInteger owings = grabOwings(new BigDecimal(bal1), new BigDecimal(total), new BigDecimal(bonus));
        assertTrue(owings.compareTo(BigInteger.ONE) > 0);
        assertEquals(owings, repo.getBalance(acc1));

        // Finally verify the other owings and their sum as well.
        BigInteger owings2 = grabOwings(new BigDecimal(bal2), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger owings3 = grabOwings(new BigDecimal(bal3), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger sum = owings.add(owings2).add(owings3);
        assertTrue(sum.compareTo(total.add(bonus)) <= 0);
        assertTrue(sum.compareTo(total.add(bonus).subtract(BigInteger.TWO)) <= 0);
    }

    @Test
    public void testWithdrawOneDepositorTotalOwings() throws InterruptedException {
        BigDecimal percent = new BigDecimal("61.13");
        BigInteger deposits = new BigInteger("435346542677");
        BigInteger bonus = new BigInteger("326543");
        int periods = 3;
        int depositors = 1;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in a non-final period greater than period 1 and withdraw.
        createBlockchain(4, TimeUnit.SECONDS.toMillis(1));
        AbstractTRS trs = newTRSstateContract(AION);
        assertEquals(BigInteger.valueOf(periods), getCurrentPeriod(trs, contract));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSmallDepositsLargeBonus() throws InterruptedException {
        BigDecimal percent = new BigDecimal("17.000012");
        BigInteger deposits = BigInteger.ONE;
        BigInteger bonus = new BigInteger("8").multiply(new BigInteger("962357283486"));
        int periods = 4;
        int depositors = 8;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We try to put the contract in a non-final period and withdraw.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        assertTrue(getCurrentPeriod(trs, contract).compareTo(BigInteger.valueOf(periods)) < 0);

        // The bonus is divisible by n depositors so we know the depositors will get back bonus/8 + 1.
        BigInteger owings = (bonus.divide(new BigInteger("8"))).add(BigInteger.ONE);
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt, repo.getBalance(acc));
        }

        // No put contract in final period and withdraw.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(5));
        assertEquals(BigInteger.valueOf(periods), getCurrentPeriod(trs, contract));
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSpecialEventIsAllFunds() throws InterruptedException {
        BigDecimal percent = new BigDecimal("100");
        BigInteger deposits = new BigInteger("23425");
        BigInteger bonus = new BigInteger("92351074341");
        int periods = 7;
        int depositors = 6;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // No blocks, so only special withdrawal is available. Since special is 100% we claim our
        // total owings right here.
        createBlockchain(0,0);
        AbstractTRS trs = newTRSstateContract(AION);
        assertEquals(0, getCurrentPeriod(trs, contract).intValue());
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Now move into a non-final period and ensure no more withdrawals can be made.
        addBlocks(1, TimeUnit.SECONDS.toMillis(1));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Now move into a final period and ensure no more withdrawals can be made.
        addBlocks(1, TimeUnit.SECONDS.toMillis(7));
        assertEquals(BigInteger.valueOf(periods), getCurrentPeriod(trs, contract));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSpecialEventVeryLarge() throws InterruptedException {
        BigDecimal percent = new BigDecimal("99.999");
        BigInteger deposits = new BigInteger("2500");
        BigInteger bonus = new BigInteger("10000");
        int periods = 6;
        int depositors = 4;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // No blocks, so only special withdrawal is available. The amount leftover from the special
        // is in the range (0,1) and so all subsequent withdrawal periods should withdraw zero until
        // the final period, where the 1 token is finally claimed.
        createBlockchain(0,0);
        AbstractTRS trs = newTRSstateContract(AION);
        assertEquals(0, getCurrentPeriod(trs, contract).intValue());
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        assertTrue(amt.compareTo(owings) < 0);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt, repo.getBalance(acc));
        }

        // Now move into a non-final period and ensure all withdrawals fail (no positive amount to claim).
        addBlocks(1, TimeUnit.SECONDS.toMillis(1));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt, repo.getBalance(acc));
        }

        // Now move into a final period and ensure we can make a withdrawal for our last coin.
        addBlocks(1, TimeUnit.SECONDS.toMillis(7));
        assertEquals(BigInteger.valueOf(periods), getCurrentPeriod(trs, contract));
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawMakeBonusDepositsAfterIsLive() throws InterruptedException {
        BigDecimal percent = new BigDecimal("10");
        BigInteger deposits = new BigInteger("2500");
        BigInteger bonus = BigInteger.ZERO;
        int periods = 2;
        int depositors = 3;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Make a large bonus deposit while contract is live.
        repo.addBalance(contract, new BigInteger("876523876532534634"));

        // Withdraw all funds and ensure it equals deposits, meaning the later bonus was ignored.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(3));
        byte[] input = getWithdrawInput(contract);
        AbstractTRS trs = newTRSstateContract(AION);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(deposits, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawContractHasOnePeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("10.1");
        BigInteger deposits = new BigInteger("2500");
        BigInteger bonus = new BigInteger("1000");
        int periods = 1;
        int depositors = 3;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        // Move into final period and withdraw.
        createBlockchain(1, 0);
        byte[] input = getWithdrawInput(contract);
        AbstractTRS trs = newTRSstateContract(AION);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSpecialPercentage18DecimalsPrecise() throws InterruptedException {
        BigDecimal percent = new BigDecimal("0.000000000000000001");
        BigInteger deposits = new BigInteger("100000000000000000000");
        BigInteger bonus = BigInteger.ZERO;
        int periods = 3;
        int depositors = 3;
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Move into non-final period and withdraw.
        createBlockchain(1, 0);

        AbstractTRS trs = newTRSstateContract(AION);
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
        assertTrue(getCurrentPeriod(trs, contract).compareTo(BigInteger.valueOf(periods)) < 0);

        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger spec = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus), percent);
        BigInteger rawAmt = getWithdrawAmt(owings, spec, periods);
        assertEquals(BigInteger.ONE, spec);
        assertEquals(amt, rawAmt.add(spec));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);

        boolean firstLook = true;
        boolean isAccNotDone;
        boolean isDone = false;
        while (!isDone) {
            isAccNotDone = false;

            for (Address acc : contributors) {
                if (newTRSuseContract(acc).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                    isAccNotDone = true;
                }
                if ((firstLook) && (getCurrentPeriod(trs, contract).intValue() == 1)) {
                    assertEquals(amt, repo.getBalance(acc));
                }
            }

            addBlocks(1, TimeUnit.SECONDS.toMillis(1));
            firstLook = false;
            if (!isAccNotDone) { isDone = true; }
        }

        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    // <-------------------------------TRS BULK-WITHDRAWAL TESTS----------------------------------->

    @Test
    public void testBulkWithdrawInputTooShort() {
        // Test maximum too-short size.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[32];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN - 1);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test minimum too-short size.
        input = new byte[1];
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testBulkWithdrawInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[34];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testBulkWithdrawCallerNotOwner() {
        BigDecimal percent = new BigDecimal("44.44");
        BigInteger deposits = new BigInteger("2652545");
        BigInteger bonus = new BigInteger("326543");
        int periods = 3;
        int depositors = 6;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Try to do a bulk-withdraw calling as every depositor in the contract (owner is AION).
        byte[] input = getBulkWithdrawInput(contract);
        AbstractTRS trs = newTRSstateContract(AION);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
        }

        // Verify no one received any funds.
        for (Address acc : contributors) {
            assertEquals(BigInteger.ZERO, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawContractNotLockedNotLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Try first with no one in the contract. Caller is owner.
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());

        // Now deposit some and try again.
        input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testBulkWithdrawContractLockedNotLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Deposit some funds and lock.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        // Try to withdraw.
        input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
    }

    @Test
    public void testBulkWithdrawOneDepositor() throws InterruptedException {
        BigDecimal percent = new BigDecimal("12.008");
        BigInteger deposits = new BigInteger("11118943432");
        BigInteger bonus = new BigInteger("346");
        int periods = 4;
        int depositors = 1;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Verify depositor has no account balance.
        AbstractTRS trs = newTRSstateContract(AION);
        Address depositor = getAllDepositors(trs, contract).iterator().next();
        assertEquals(BigInteger.ZERO, repo.getBalance(depositor));

        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        assertTrue(getContractCurrentPeriod(trs, contract) < periods);

        // Do a bulk-withdraw from the contract.
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, deposits, bonus, percent, periods);
        assertEquals(amt, repo.getBalance(depositor));
    }

    @Test
    public void testBulkWithdrawMultipleDepositors() throws InterruptedException {
        BigDecimal percent = new BigDecimal("61.987653");
        BigInteger deposits = new BigInteger("346264344");
        BigInteger bonus = new BigInteger("18946896534");
        int periods = 3;
        int depositors = 6;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        // Do a bulk-withdraw on the contract.
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());

        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(amt, repo.getBalance(acc));
        }

        // Move into final period and withdraw the rest.
        addBlocks(1, TimeUnit.SECONDS.toMillis(4));
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawSomeDepositorsHaveWithdrawnSomeNotThisPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("74.237");
        BigInteger deposits = new BigInteger("3436");
        BigInteger bonus = new BigInteger("345");
        int periods = 3;
        int depositors = 5;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        // Have half of the depositors withdraw.
        byte[] input = getWithdrawInput(contract);
        boolean withdraw = true;
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            if (withdraw) {
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
                assertEquals(amt, repo.getBalance(acc));
            } else {
                assertEquals(BigInteger.ZERO, repo.getBalance(acc));
            }
            withdraw = !withdraw;
        }

        // Do a bulk-withdraw on the contract. Check all accounts have amt now and no one has more.
        input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(amt, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawSpecialEventBeforeLiveBlockArrives() throws InterruptedException {
        BigDecimal percent = new BigDecimal("21");
        BigInteger deposits = new BigInteger("324876");
        BigInteger bonus = new BigInteger("3").pow(13);
        int periods = 4;
        int depositors = 3;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We have only a genesis block so by block timestamps we are not yet live.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(0, 0);
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));

        // Do a bulk-withdraw on the contract. Check all accounts have received only the special
        // withdrawal amount and nothing more.
        BigInteger spec = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus), percent);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(spec, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawAtFinalPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("17.793267");
        BigInteger deposits = new BigInteger("96751856431");
        BigInteger bonus = new BigInteger("3274436");
        int periods = 4;
        int depositors = 12;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Move contract into its final period.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(5));
        assertEquals(getCurrentPeriod(trs, contract).intValue(), periods);

        // Verify each depositor gets their total owings back (and that the sum they collect is available)
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        assertTrue((owings.multiply(BigInteger.valueOf(depositors))).compareTo(total.add(bonus)) <= 0);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawMultipleTimesSpecialPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("21");
        BigInteger deposits = new BigInteger("324876");
        BigInteger bonus = new BigInteger("3").pow(13);
        int periods = 4;
        int depositors = 3;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // We have only a genesis block so by block timestamps we are not yet live.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(0, 0);
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));

        // Do a bulk-withdraw on the contract. Check all accounts have received only the special
        // withdrawal amount and nothing more.
        BigInteger spec = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus), percent);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(spec, repo.getBalance(acc));
        }

        // Attempt to do another bulk withdraw in this same period; account balances should not change.
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(spec, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawMultipleTimesNonFinalPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("43");
        BigInteger deposits = new BigInteger("3744323");
        BigInteger bonus = new BigInteger("8347634");
        int periods = 6;
        int depositors = 8;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Move contract into a non-final period.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));

        // Do a bulk-withdraw on the contract. Check all accounts have received amt.
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(amt, repo.getBalance(acc));
        }

        // Attempt to do another bulk withdraw in this same period; account balances should not change.
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(amt, repo.getBalance(acc));
        }
    }

    @Test
    public void testBulkWithdrawMultipleTimesFinalPeriod() throws InterruptedException {
        BigDecimal percent = new BigDecimal("2.0001");
        BigInteger deposits = new BigInteger("1000000000");
        BigInteger bonus = new BigInteger("650000");
        int periods = 2;
        int depositors = 11;
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Move contract into a non-final period.
        AbstractTRS trs = newTRSstateContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(3));
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));

        // Do a bulk-withdraw on the contract. Check all accounts have received amt.
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getBulkWithdrawInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
        }

        // Attempt to do another bulk withdraw in this same period; account balances should not change.
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    // <--------------------------------------REFUND TRS TESTS------------------------------------->

    @Test
    public void testRefundInputTooShort() {
        // Test maximum too-short size.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[192];
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test minimum too-short size.
        input = new byte[1];
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundInputTooLarge() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[194];
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundBadTRScontract() {
        // Test TRS address that looks like regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getRefundInput(contract, acct, BigInteger.ZERO);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Test TRS address with TRS prefix, so it looks legit.
        byte[] addr = ECKeyFac.inst().create().getAddress();
        addr[0] = (byte) 0xC0;
        contract = new Address(addr);
        tempAddrs.add(contract);
        input = getRefundInput(contract, acct, BigInteger.ZERO);
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundCallerIsNotOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // acct2 deposits so that it does have a balance to refund from.
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        TRSuseContract trs = newTRSuseContract(acct2);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());

        // acct2 calls refund but owner is acct
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundAccountNotInContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // acct2 has never deposited and is not a valid account in the contract yet.
        byte[] input = getRefundInput(contract, acct2, BigInteger.ONE);
        TRSuseContract trs = newTRSuseContract(acct2);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        // Have others deposit but not acct2 and try again ... should be same result.
        Address acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct4 = getNewExistentAccount(DEFAULT_BALANCE);

        input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getResultCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct4).execute(input, COST).getResultCode());

        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = newTRSuseContract(acct2).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundContractIsLocked() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Have acct2 deposit some balance.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());

        // Now lock the contract.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        // Now have contract owner try to refund acct2.
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundContractIsLive() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Have acct2 deposit some balance.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());

        // Now lock the contract and make it live.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());
        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());

        // Now have contract owner try to refund acct2.
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundAccountBalanceInsufficient() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Have acct2 deposit some balance.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());

        // Now have contract owner try to refund acct2 for more than acct2 has deposited.
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE.add(BigInteger.ONE));
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundAccountFullBalance() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct2));

        // Have acct2 deposit some balance.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct2);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertTrue(accountIsValid(trs, contract, acct2));

        // Now have contract owner try to refund acct2 for exactly what acct2 has deposited.
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct2));
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct2));
        assertFalse(accountIsValid(trs, contract, acct2));
    }

    @Test
    public void testRefundAccountFullBalance2() {
        // Same as above test but we test here a balance that spans multiple storage rows.
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(max);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        assertEquals(max, repo.getBalance(acct));

        byte[] input = getMaxDepositInput(contract);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(max, getDepositBalance(trs, contract, acct));
        assertEquals(max, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertTrue(accountIsValid(trs, contract, acct));

        input = getMaxRefundInput(contract, acct);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct));
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
        assertEquals(max, repo.getBalance(acct));
        assertFalse(accountIsValid(trs, contract, acct));
    }

    @Test
    public void testRefundAccountBalanceLeftover() {
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(max);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        assertEquals(max, repo.getBalance(acct));

        BigInteger depositAmt = new BigInteger("897326236725789012");
        byte[] input = getDepositInput(contract, depositAmt);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(depositAmt, getDepositBalance(trs, contract, acct));
        assertEquals(depositAmt, getTotalBalance(trs, contract));
        assertEquals(max.subtract(depositAmt), repo.getBalance(acct));
        assertTrue(accountIsValid(trs, contract, acct));

        BigInteger diff = new BigInteger("23478523");
        input = getRefundInput(contract, acct, depositAmt.subtract(diff));
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertEquals(diff, getDepositBalance(trs, contract, acct));
        assertEquals(diff, getTotalBalance(trs, contract));
        assertEquals(max.subtract(diff), repo.getBalance(acct));
        assertTrue(accountIsValid(trs, contract, acct));
    }

    @Test
    public void testRefundTotalBalanceMultipleAccounts() {
        BigInteger funds1 = new BigInteger("439378943235235");
        BigInteger funds2 = new BigInteger("298598364");
        BigInteger funds3 = new BigInteger("9832958020263806345437400898000");
        Address acct1 = getNewExistentAccount(funds1);
        Address acct2 = getNewExistentAccount(funds2);
        Address acct3 = getNewExistentAccount(funds3);
        Address contract = createTRScontract(acct1, false, true, 1,
            BigInteger.ZERO, 0);

        // Make some deposits.
        byte[] input = getDepositInput(contract, funds1);
        TRSuseContract trs = newTRSuseContract(acct1);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        input = getDepositInput(contract, funds2);
        trs = newTRSuseContract(acct2);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        input = getDepositInput(contract, funds3);
        trs = newTRSuseContract(acct3);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(funds1, getDepositBalance(trs, contract, acct1));
        assertEquals(funds2, getDepositBalance(trs, contract, acct2));
        assertEquals(funds3, getDepositBalance(trs, contract, acct3));
        assertEquals(funds1.add(funds2).add(funds3), getTotalBalance(trs, contract));
        assertTrue(accountIsValid(trs, contract, acct1));
        assertTrue(accountIsValid(trs, contract, acct2));
        assertTrue(accountIsValid(trs, contract, acct3));

        // Make some refunds.
        BigInteger diff1 = new BigInteger("34645642");
        BigInteger diff2 = new BigInteger("196254756");
        input = getRefundInput(contract, acct1, funds1.subtract(diff1));
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getRefundInput(contract, acct2, funds2.subtract(diff2));
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());
        input = getRefundInput(contract, acct3, funds3);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getResultCode());

        assertEquals(diff1, getDepositBalance(trs, contract, acct1));
        assertEquals(diff2, getDepositBalance(trs, contract, acct2));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct3));
        assertEquals(diff1.add(diff2), getTotalBalance(trs, contract));
        assertTrue(accountIsValid(trs, contract, acct1));
        assertTrue(accountIsValid(trs, contract, acct2));
        assertFalse(accountIsValid(trs, contract, acct3));
    }

    @Test
    public void testRefundInvalidAccount() {
        // We make an account invalid by depositing and then fully refunding it.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct2));

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct2);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertTrue(accountIsValid(trs, contract, acct2));

        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());

        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct2));
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct2));
        assertFalse(accountIsValid(trs, contract, acct2));

        // Now try to refund acct2 again...
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundZeroForNonExistentAccount() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getRefundInput(contract, acct2, BigInteger.ZERO);
        TRSuseContract trs = newTRSuseContract(acct);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundZeroForInvalidAccount() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        // Acct2 is now marked invalid.
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testRefundZeroForValidAccount() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());

        // Now try to refund nothing, acct2 exists in the contract.
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getResultCode());

        // Verify nothing actually changed.
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
    }

    @Test
    public void testRefundSuccessNrgLeft() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getResultCode());

        long diff = 47835;
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST + diff);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(diff, res.getNrgLeft());
    }

    // <---------------------------------TRS DEPOSIT-FOR TESTS------------------------------------->
    // NOTE: the actual deposit logic occurs on 1 code path, which deposit and depositFor share. The
    // deposit tests have more extensive tests for correctness.

    @Test
    public void testDepositForInputTooLong() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, acct, BigInteger.ONE);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);

        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testDepositForInputTooShort() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, acct, BigInteger.ONE);
        byte[] shortInput = new byte[input.length - 1];
        System.arraycopy(input, 0, shortInput, 0, input.length - 1);

        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testDepositForContractIsLocked() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, acct, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForContractIsLive() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createLockedAndLiveTRScontract(acct, false, false,
            1, BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, acct, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForCallerIsNotOwner() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address whoami = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, whoami, DEFAULT_BALANCE);
        ExecutionResult res = newTRSuseContract(whoami).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForCallerHasInsufficientFunds() {
        // Owner does not have adequate funds but the recipient of the deposit-for does.
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address other = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, other, DEFAULT_BALANCE);
        ExecutionResult res = newTRSuseContract(owner).execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForNonAionAddressAsDepositor() {
        // The deposit-for recipient is another trs contract...
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address other = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositForInput(contract, other, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(owner).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForContractAddressIsInvalid() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address other = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] addr = Arrays.copyOf(other.toBytes(), other.toBytes().length);
        addr[0] = (byte) 0xC0;
        Address contract = new Address(addr);

        byte[] input = getDepositForInput(contract, other, BigInteger.ONE);
        ExecutionResult res = newTRSuseContract(owner).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositForZeroAmount() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address other = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        // Verify other has zero balance after this (also owner just to make sure)
        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = getDepositForInput(contract, other, BigInteger.ZERO);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, other));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, owner));
        assertEquals(BigInteger.ONE, repo.getBalance(owner));
    }

    @Test
    public void testDepositForOneAccount() {
        // other does not need any funds.
        Address owner = getNewExistentAccount(DEFAULT_BALANCE);
        Address other = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        // Verify other has zero balance after this (also owner just to make sure)
        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = getDepositForInput(contract, other, DEFAULT_BALANCE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, other));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, owner));
        assertEquals(BigInteger.ZERO, repo.getBalance(owner));
    }

    @Test
    public void testDepositForMultipleAccounts() {
        BigInteger balance = new BigInteger("832523626");
        Address owner = getNewExistentAccount(balance.multiply(BigInteger.valueOf(4)));
        Address other1 = getNewExistentAccount(BigInteger.ZERO);
        Address other2 = getNewExistentAccount(BigInteger.ZERO);
        Address other3 = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = getDepositForInput(contract, other1, balance);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        input = getDepositForInput(contract, other2, balance);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        input = getDepositForInput(contract, other3, balance);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        assertEquals(balance, getDepositBalance(trs, contract, other1));
        assertEquals(balance, getDepositBalance(trs, contract, other2));
        assertEquals(balance, getDepositBalance(trs, contract, other3));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, owner));
        assertEquals(balance, repo.getBalance(owner));
    }

    @Test
    public void testDepositForSameAccountMultipleTimes() {
        BigInteger balance = new BigInteger("8293652893346342674375477457554345");
        int times = 61;
        Address owner = getNewExistentAccount((balance.multiply(BigInteger.TWO)).
            multiply(BigInteger.valueOf(times)));
        Address other1 = getNewExistentAccount(BigInteger.ZERO);
        Address other2 = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = getDepositForInput(contract, other1, balance);
        for (int i = 0; i < times; i++) {
            assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        }
        input = getDepositForInput(contract, other2, balance);
        for (int i = 0; i < times; i++) {
            assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        }

        assertEquals(balance.multiply(BigInteger.valueOf(times)), getDepositBalance(trs, contract, other1));
        assertEquals(balance.multiply(BigInteger.valueOf(times)), getDepositBalance(trs, contract, other2));
        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, owner));
        assertEquals(BigInteger.ZERO, repo.getBalance(owner));
    }

    @Test
    public void testDepositForOneself() {
        // No reason why an owner can't use depositFor to deposit on his/her own behalf.
        Address owner = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        // Verify other has zero balance after this (also owner just to make sure)
        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = getDepositForInput(contract, owner, DEFAULT_BALANCE);
        ExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, owner));
        assertEquals(BigInteger.ZERO, repo.getBalance(owner));
    }

    // <-------------------------------TRS ADD EXTRA FUNDS TESTS----------------------------------->

    @Test
    public void testAddExtraInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getAddExtraInput(contract, DEFAULT_BALANCE);
        byte[] shortInput = Arrays.copyOf(input, input.length - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testAddExtraInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getAddExtraInput(contract, DEFAULT_BALANCE);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testAddExtraContractNonExistent() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getAddExtraInput(acct, DEFAULT_BALANCE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAddExtraCallerIsNotOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(AION, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getAddExtraInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAddExtraCallHasInsufficientFunds() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getAddExtraInput(contract, DEFAULT_BALANCE.add(BigInteger.ONE));
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAddExtraFundsOpen() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getOpenFundsInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSstateContract(acct).execute(input, COST).getResultCode());
        assertTrue(getAreContractFundsOpen(newTRSstateContract(acct), contract));

        input = getAddExtraInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testExtraFundsNewContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        assertEquals(BigInteger.ZERO, getExtraFunds(trs, contract));
    }

    @Test
    public void testAddZeroExtraFunds() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        byte[] input = getAddExtraInput(contract, BigInteger.ZERO);
        assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getResultCode());
        assertEquals(BigInteger.ZERO, getExtraFunds(trs, contract));
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct));
    }

    @Test
    public void testAddExtraFundsUnlocked() {
        BigInteger amt = new BigInteger("32985623956237896532753265332");
        Address acct = getNewExistentAccount(amt);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        byte[] input = getAddExtraInput(contract, amt);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertEquals(amt, getExtraFunds(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
    }

    @Test
    public void testAddExtraFundsLocked() {
        BigInteger amt = new BigInteger("32985623956237896532753265332").add(BigInteger.ONE);
        Address acct = getNewExistentAccount(amt);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        byte[] input = getAddExtraInput(contract, amt.subtract(BigInteger.ONE));
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertEquals(amt.subtract(BigInteger.ONE), getExtraFunds(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
    }

    @Test
    public void testAddExtraFundsLive() {
        BigInteger amt = new BigInteger("32985623956237896532753265332").add(BigInteger.ONE);
        Address acct = getNewExistentAccount(amt);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        byte[] input = getAddExtraInput(contract, amt.subtract(BigInteger.ONE));
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        assertEquals(amt.subtract(BigInteger.ONE), getExtraFunds(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
    }

    @Test
    public void testAddMaxExtraFunds() {
        int times = 23;
        BigInteger amt = getMaxOneTimeDeposit().multiply(BigInteger.valueOf(times));
        Address acct = getNewExistentAccount(amt);
        Address contract = createTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(acct);
        byte[] input = getAddExtraMaxInput(contract);
        for (int i = 0; i < times; i++) {
            assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        }
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(amt, getExtraFunds(trs, contract));
    }

    @Test
    public void testAddExtraDuringSpecialWithdrawPeriod() throws InterruptedException {
        int numDepositors = 7;
        int periods = 4;
        BigInteger deposits = new BigInteger("2389562389532434346345634");
        BigInteger bonus = new BigInteger("237856238756235");
        BigInteger extra = new BigInteger("32865237523");
        BigDecimal percent = new BigDecimal("41.12221");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        // In the special-only period, extra funds are unable to be withdrawn.
        AbstractTRS trs = newTRSuseContract(AION);
        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        createBlockchain(0, 0);

        Set<Address> contributors = getAllDepositors(trs, contract);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        for (Address acc : contributors) {
            BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
            input = getWithdrawInput(contract);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt, repo.getBalance(acc));
        }
    }

    @Test
    public void testAddExtraNonFinalPeriod() throws InterruptedException {
        int numDepositors = 4;
        int periods = 8;
        BigInteger deposits = new BigInteger("238752378652");
        BigInteger bonus = new BigInteger("23454234");
        BigInteger extra = new BigInteger("43895634825643872563478934");
        BigDecimal percent = new BigDecimal("1");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSuseContract(AION);
        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));
        int currPeriod = getContractCurrentPeriod(trs, contract);
        assertTrue(currPeriod < periods);
        checkPayoutsNonFinal(trs, contract, numDepositors, deposits, bonus, percent, periods, currPeriod);
    }

    @Test
    public void testAddExtraFinalPeriod() throws InterruptedException {
        int numDepositors = 14;
        int periods = 2;
        BigInteger deposits = new BigInteger("2358325346");
        BigInteger bonus = new BigInteger("5454534");
        BigInteger extra = new BigInteger("34238462353567234");
        BigDecimal percent = new BigDecimal("16");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSuseContract(AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(3));
        int currPeriod = getContractCurrentPeriod(trs, contract);
        assertEquals(currPeriod, periods);

        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        checkPayoutsFinal(trs, contract, numDepositors, deposits, bonus, extra);
    }

    @Test
    public void testAddExtraWithdrawMultipleTimesSpecialPeriod() throws InterruptedException {
        int numDepositors = 7;
        int periods = 4;
        BigInteger deposits = new BigInteger("243346234453");
        BigInteger bonus = new BigInteger("436436343434");
        BigInteger extra = new BigInteger("457457457454856986786534");
        BigDecimal percent = new BigDecimal("11.123");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        AbstractTRS trs = newTRSuseContract(AION);
        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        createBlockchain(0, 0);

        // We have half the contributors withdraw now. No extras are collected.
        Set<Address> contributors = getAllDepositors(trs, contract);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));

        input = getWithdrawInput(contract);
        boolean withdraw = true;
        for (Address acc : contributors) {
            BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
            if (withdraw) {
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
                assertEquals(amt, repo.getBalance(acc));
            } else {
                assertEquals(BigInteger.ZERO, repo.getBalance(acc));
            }
            withdraw = !withdraw;
        }

        // Add more extra funds into contract, everyone withdraws. The previous withdrawers should
        // be unable to withdraw and have same balance as before but new ones should get the new
        // extra shares.
        repo.addBalance(AION, extra);
        input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        input = getWithdrawInput(contract);
        withdraw = true;
        for (Address acc : contributors) {
            BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
            if (withdraw) {
                // These accounts have already withdrawn this period.
                assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
            } else {
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            }
            assertEquals(amt, repo.getBalance(acc));
            withdraw = !withdraw;
        }
    }

    @Test
    public void testAddExtraWithdrawMultipleTimesSameNonFinalPeriod() throws InterruptedException {
        int numDepositors = 17;
        int periods = 8;
        BigInteger deposits = new BigInteger("43436454575475437");
        BigInteger bonus = new BigInteger("325436346546345634634634346");
        BigInteger extra = new BigInteger("1000");
        BigDecimal percent = new BigDecimal("18.888");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        // We add some extra funds and half the accounts make a withdrawal, half don't.
        AbstractTRS trs = newTRSuseContract(AION);
        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));
        int currPeriod = getContractCurrentPeriod(trs, contract);
        assertTrue(currPeriod < periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigDecimal fraction = BigDecimal.ONE.divide(BigDecimal.valueOf(numDepositors), 18, RoundingMode.HALF_DOWN);

        BigInteger prevAmt = null;
        input = getWithdrawInput(contract);
        boolean withdraw = true;
        for (Address acc : contributors) {
            BigInteger extraShare = getExtraShare(trs, contract, acc, fraction, currPeriod);
            BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
            prevAmt = amt.add(extraShare);
            if (withdraw) {
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
                assertEquals(amt.add(extraShare), repo.getBalance(acc));
            } else {
                assertEquals(BigInteger.ZERO, repo.getBalance(acc));
            }
            withdraw = !withdraw;
        }

        // Now we add more extra funds and have everyone withdraw. The ones who have withdrawn
        // already will fail and the new ones will collect more.
        repo.addBalance(AION, extra);
        input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());

        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
        input = getWithdrawInput(contract);
        withdraw = true;
        for (Address acc : contributors) {
            BigInteger extraShare = getExtraShare(trs, contract, acc, fraction, currPeriod);
            if (withdraw) {
                // These have already withdrawn.
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
                assertEquals(prevAmt.add(extraShare), repo.getBalance(acc));
            } else {
                assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
                assertEquals(amt.add(extraShare), repo.getBalance(acc));
                assertTrue(prevAmt.compareTo(amt.add(extraShare)) < 0);
            }
            withdraw = !withdraw;
        }

        // Verify that after this everyone has collected the same amount.
        BigInteger bal = null;
        for (Address acc : contributors) {
            if (bal == null) {
                bal = repo.getBalance(acc);
            } else {
                assertEquals(bal, repo.getBalance(acc));
            }
        }
    }

    @Test
    public void testAddExtraWithdrawMultipleTimesFinalPeriod() throws InterruptedException {
        int numDepositors = 11;
        int periods = 1;
        BigInteger deposits = new BigInteger("3523345345");
        BigInteger bonus = new BigInteger("877654634456");
        BigInteger extra = new BigInteger("2213322345");
        BigDecimal percent = new BigDecimal("27");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);

        // Add the extra funds and then withdraw everything.
        AbstractTRS trs = newTRSuseContract(AION);
        repo.addBalance(AION, extra);
        byte[] input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));
        int currPeriod = getContractCurrentPeriod(trs, contract);
        assertEquals(currPeriod, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        BigDecimal fraction = BigDecimal.ONE.divide(BigDecimal.valueOf(numDepositors), 18, RoundingMode.HALF_DOWN);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger prevAmt = null;
        for (Address acc : contributors) {
            BigInteger share = getExtraShare(trs, contract, acc, fraction, currPeriod);
            input = getWithdrawInput(contract);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(owings.add(share), repo.getBalance(acc));
            prevAmt = owings.add(share);
        }

        // Now add more extra funds in and each person should be able to withdraw their share.
        repo.addBalance(AION, extra);
        input = getAddExtraInput(contract, extra);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        for (Address acc : contributors) {
            BigInteger share = getExtraShare(trs, contract, acc, fraction, currPeriod);
            input = getWithdrawInput(contract);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(prevAmt.add(share), repo.getBalance(acc));
        }

        // Attempt to withdraw again, everyone should fail now because all extras have been claimed.
        for (Address acc : contributors) {
            BigInteger share = getExtraShare(trs, contract, acc, fraction, currPeriod);
            input = getWithdrawInput(contract);
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getResultCode());
        }
    }

    @Test
    public void testAddExtraMultipleTimesMultipleWithdrawsOverContractLifetime() throws InterruptedException {
        int numDepositors = 7;
        int periods = 5;
        BigInteger deposits = new BigInteger("11122223334455555");
        BigInteger bonus = new BigInteger("82364545474572");
        BigInteger extra = new BigInteger("563233323552");
        BigDecimal percent = new BigDecimal("12.05");
        Address contract = setupContract(numDepositors, deposits, bonus, periods, percent);
        createBlockchain(0, 0);

        // Each loop we deposit some extra balance and have everyone withdraw. This loop has users
        // withdrawing multiple times per each period until done.
        AbstractTRS trs = newTRSuseContract(AION);
        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input;
        BigInteger extraSum = BigInteger.ZERO;
        int attemptsPerPeriod = 3;
        for (int i = 0; i < periods * attemptsPerPeriod; i++) {
            if (i % (attemptsPerPeriod - 1) == 0) {
                repo.addBalance(AION, extra);
                input = getAddExtraInput(contract, extra);
                assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
                extraSum = extraSum.add(extra);
            }

            for (Address acc : contributors) {
                input = getWithdrawInput(contract);
                newTRSuseContract(acc).execute(input, COST);
            }

            if (i % attemptsPerPeriod == 0) {
                addBlocks(1, TimeUnit.SECONDS.toMillis(1));
            }
        }

        // now check each account has expected balance and contract did not dish out more than it had.
        BigDecimal fraction = BigDecimal.ONE.
            divide(BigDecimal.valueOf(numDepositors), 18, RoundingMode.HALF_DOWN);
        BigInteger bonusShare = fraction.multiply(new BigDecimal(bonus)).toBigInteger();
        BigInteger extraShare = fraction.multiply(new BigDecimal(extraSum)).toBigInteger();
        BigInteger expectedAmt = deposits.add(bonusShare).add(extraShare);
        for (Address acc : contributors) {
            assertEquals(expectedAmt, repo.getBalance(acc));
        }

        BigInteger contractTotal = deposits.multiply(BigInteger.valueOf(numDepositors)).
            add(bonus).add(extraSum);
        assertTrue((expectedAmt.multiply(BigInteger.valueOf(numDepositors))).compareTo(contractTotal) <= 0);
    }

    // <------------------------------TRS BULK-DEPOSIT-FOR TESTS----------------------------------->

    @Test
    public void testBulkDepositForInputTooShort() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        int numBeneficiaries = 1;
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);
        byte[] shortInput = Arrays.copyOf(input, input.length - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForInputTooLong() {
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);

        int numBeneficiaries = 100;
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForContractNonExistent() {
        int numBeneficiaries = 3;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        byte[] input = makeBulkDepositForInput(acct, numBeneficiaries, BigInteger.TEN);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForCallerIsNotOwner() {
        int numBeneficiaries = 5;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        Address whoami = getNewExistentAccount(BigInteger.TEN.multiply(BigInteger.valueOf(numBeneficiaries)));
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(whoami).execute(input, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForContractLocked() {
        int numBeneficiaries = 4;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createAndLockTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForContractLive() {
        int numBeneficiaries = 7;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createLockedAndLiveTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForZeroBeneficiaries() {
        int numBeneficiaries = 1;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);

        // Remove the 1 beneficiary.
        byte[] noBeneficiaries = new byte[33];
        System.arraycopy(input, 0, noBeneficiaries, 0, Address.ADDRESS_LEN + 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(noBeneficiaries, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForBeneficiaryLengthOff() {
        int numBeneficiaries = 1;
        Address acct = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, BigInteger.TEN);

        // Remove the last byte of the array.
        byte[] inputOff = new byte[input.length - 1];
        System.arraycopy(input, 0, inputOff, 0, input.length - 1);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(inputOff, COST).getResultCode());

        // Add a byte to the array.
        inputOff = new byte[input.length + 1];
        System.arraycopy(input, 0, inputOff, 0, input.length);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(inputOff, COST).getResultCode());
    }

    @Test
    public void testBulkDepositForSelfIncluded() {
        int numBeneficiaries = 12;
        BigInteger deposits = new BigInteger("3462363223");
        Address owner = getNewExistentAccount(deposits.multiply(BigInteger.valueOf(numBeneficiaries + 1)));
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInputwithSelf(contract, owner, numBeneficiaries, deposits);

        AbstractTRS trs = newTRSuseContract(owner);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        assertEquals(numBeneficiaries + 1, contributors.size());
        assertTrue(contributors.contains(owner));
        for (Address acc : contributors) {
            assertEquals(deposits, getDepositBalance(trs, contract, acc));
        }
        assertEquals(deposits.multiply(BigInteger.valueOf(numBeneficiaries + 1)), getTotalBalance(trs, contract));
    }

    @Test
    public void testBulkDepositForOneBeneficiary() {
        BigInteger deposits = new BigInteger("111112222");
        Address owner = getNewExistentAccount(deposits);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = makeBulkDepositForInput(contract, 1, deposits);

        AbstractTRS trs = newTRSuseContract(owner);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        assertEquals(1, contributors.size());
        assertEquals(deposits, getDepositBalance(trs, contract, contributors.iterator().next()));
        assertEquals(deposits, getTotalBalance(trs, contract));
    }

    @Test
    public void testBulkDepositFor100Beneficiaries() {
        BigInteger deposits = new BigInteger("345654399");
        BigInteger total = deposits.multiply(BigInteger.valueOf(100));
        Address owner = getNewExistentAccount(total);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        byte[] input = makeBulkDepositForInput(contract, 100, deposits);
        AbstractTRS trs = newTRSuseContract(owner);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        assertEquals(100, contributors.size());
        for (Address acc : contributors) {
            assertEquals(deposits, getDepositBalance(trs, contract, acc));
        }
        assertEquals(total, getTotalBalance(trs, contract));
    }

    @Test
    public void testBulkDepositForInsufficientFundsFirstDeposit() {
        int numBeneficiaries = 13;
        BigInteger deposits = new BigInteger("111112222");
        BigInteger amt = deposits.subtract(BigInteger.ONE);
        Address owner = getNewExistentAccount(amt);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, deposits);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        assertTrue(contributors.isEmpty());
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
    }

    @Test
    public void testBulkDepositForInsufficientFundsLastDeposit() {
        int numBeneficiaries = 23;
        BigInteger deposits = new BigInteger("111112222");
        BigInteger amt = (deposits.multiply(BigInteger.valueOf(numBeneficiaries))).subtract(BigInteger.ONE);
        Address owner = getNewExistentAccount(amt);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries, deposits);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        assertTrue(contributors.isEmpty());
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
    }

    @Test
    public void testBulkDepositForInsufficientFundsMidDeposit() {
        int numBeneficiaries = 13;
        int diff = 3;
        BigInteger deposits = new BigInteger("111112222");
        BigInteger amt = deposits.multiply(BigInteger.valueOf(numBeneficiaries - diff).subtract(BigInteger.ONE));
        Address owner = getNewExistentAccount(amt);
        Address contract = createTRScontract(owner, false, false, 1,
            BigInteger.ZERO, 0);

        AbstractTRS trs = newTRSuseContract(owner);
        byte[] input = makeBulkDepositForInput(contract, numBeneficiaries - diff - 1, deposits);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getResultCode());
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(deposits, getDepositBalance(trs, contract, acc));
        }
        assertEquals(deposits.multiply(BigInteger.valueOf(numBeneficiaries - diff - 1)), getTotalBalance(trs, contract));

        // Verify no one received any deposits and that previous total is unchanged.
        input = makeBulkDepositForInput(contract, diff, deposits);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, trs.execute(input, COST).getResultCode());
        Set<Address> contributors2 = getAllDepositors(trs, contract);
        assertEquals(contributors.size(), contributors2.size());
        assertEquals(contributors, contributors2);
        for (Address acc : contributors) {
            assertEquals(deposits, getDepositBalance(trs, contract, acc));
        }
        assertEquals(deposits.multiply(BigInteger.valueOf(numBeneficiaries - diff - 1)), getTotalBalance(trs, contract));
    }

}
