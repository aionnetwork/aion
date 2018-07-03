package org.aion.precompiled.TRS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.TRSuseContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSuseContract API.
 */
public class TRSuseContractTest extends TRShelpers {
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

    // Returns a new TRSuseContract and calls the contract using caller.
    private TRSuseContract newTRSuseContract(Address caller) {
        return new TRSuseContract(repo, caller);
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation.
    private byte[] getDepositInput(Address contract, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { fail(); }
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length , amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation, to
    // deposit the maximum allowable amount.
    private byte[] getMaxDepositInput(Address contract) {
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        for (int i = 33; i <= 160; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns the maximum amount that can be deposited in a single deposit call.
    private BigInteger getMaxOneTimeDeposit() {
        return BigInteger.TWO.pow(1024).subtract(BigInteger.ONE);
    }

    // Returns the maximum amount that a single account can deposit into a TRS contract.
    private BigInteger getMaxTotalDeposit() {
        return BigInteger.TWO.pow(4096).subtract(BigInteger.ONE);
    }

    // Returns true only if caller has a positive deposit balance in the TRS contract contract.
    private boolean fetchAccountHasPositiveDepositBalance(Address contract, Address caller) {
        return repo.getStorageValue(contract, new DoubleDataWord(caller.toBytes())) != null;
    }

    // Returns the deposit balance associated with account in the TRS contract contract or null if
    // no balance.
    private BigInteger fetchDepositBalance(Address contract, Address account) {
        IDataWord val = repo.getStorageValue(contract, newIDataWord(account.toBytes()));
        if (val == null) { return null; }
        int numRows = (val.getData()[0] & 0x0F);

        byte[] key = new byte[DoubleDataWord.BYTES];
        byte[] amt = new byte[(numRows * DoubleDataWord.BYTES) + 1];    // + 1 for unsigned.
        for (int i = 0; i < numRows; i++) {
            key[0] = (byte) (0xB0 | i);
            System.arraycopy(account.toBytes(), 1, key, 1, DoubleDataWord.BYTES - 1);
            System.arraycopy(repo.getStorageValue(contract, newIDataWord(key)).getData(),
                0, amt, (i * DoubleDataWord.BYTES) + 1, DoubleDataWord.BYTES);
        }
        return new BigInteger(amt);
    }

    // Returns the head of the list for contract or null if no head.
    private Address fetchLinkedListHead(Address contract) {
        byte[] listKey = new byte[DoubleDataWord.BYTES];
        listKey[0] = 0x70;
        IDataWord head = repo.getStorageValue(contract, newIDataWord(listKey));
        if (head == null) { return null; }
        if ((head.getData()[0] & 0x80) == 0x80) { return null; }
        byte[] headAddr = new byte[Address.ADDRESS_LEN];
        headAddr[0] = (byte) 0xA0;
        System.arraycopy(head.getData(), 1, headAddr, 1, Address.ADDRESS_LEN - 1);
        return new Address(headAddr);
    }

    // Returns the next account in the linked list after current, or null if no next.
    private Address fetchLinkedListNext(Address contract, Address current) {
        IDataWord next = repo.getStorageValue(contract, newIDataWord(current.toBytes()));
        if (next == null) { return null; }
        byte[] nextBytes = next.getData();
        if ((nextBytes[0] & 0x80) == 0x00) { return null; } // entry invalid
        if ((nextBytes[0] & 0x40) == 0x40) { return null; } // null bit set
        byte[] nextAddr = new byte[Address.ADDRESS_LEN];
        nextAddr[0] = (byte) 0xA0;
        System.arraycopy(nextBytes, 1, nextAddr, 1, Address.ADDRESS_LEN - 1);
        return new Address(nextAddr);
    }

    // Returns the previous account in the linked list prior to current, or null if no previous.
    private Address fetchLinkedListPrev(Address contract, Address current) {
        byte[] prevKey = new byte[DoubleDataWord.BYTES];
        prevKey[0] = 0x60;
        System.arraycopy(current.toBytes(), 1, prevKey, 1, Address.ADDRESS_LEN - 1);
        IDataWord prev = repo.getStorageValue(contract, newIDataWord(prevKey));
        if (prev == null) { return null; }
        byte[] prevBytes = prev.getData();
        if ((prevBytes[0] & 0x80) == 0x80) { return null; } // null bit set
        byte[] prevAddr = new byte[Address.ADDRESS_LEN];
        prevAddr[0] = (byte) 0xA0;
        System.arraycopy(prevBytes, 1, prevAddr, 1, Address.ADDRESS_LEN - 1);
        return new Address(prevAddr);
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSuseContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSuseContract trs = newTRSuseContract(addr);
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
        TRSuseContract trs = newTRSuseContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        ContractExecutionResult res;
        for (int i = 0; i <= useCurrMaxOp; i++) {
            res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
            assertEquals(ResultCode.INVALID_NRG_LIMIT, res.getCode());
            assertEquals(0, res.getNrgLeft());
        }
    }

    // <-------------------------------------DEPOSIT TRS TESTS------------------------------------->

    @Test
    public void testDepositInputTooShort() {
        // Test on minimum too-small amount.
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        byte[] input = new byte[1];
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-small amount.
        input = new byte[160];
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositInputTooLong() {
        TRSuseContract trs = newTRSuseContract(getNewExistentAccount(BigInteger.ZERO));
        byte[] input = new byte[162];
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositToNonExistentContract() {
        // Test on contract address actually an account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(acct, BigInteger.TWO);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on contract address looks like a legit TRS address (proper prefix).
        byte[] addr = new byte[Address.ADDRESS_LEN];
        System.arraycopy(acct.toBytes(), 0, addr, 0, Address.ADDRESS_LEN);
        addr[0] = (byte) 0xC0;

        input = getDepositInput(Address.wrap(addr), BigInteger.TWO);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-large amount.
        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test in test mode.
        // Test on minimum too-large amount.
        contract = createTRScontract(AION, true, true, 1, BigInteger.ZERO, 0);
        input = getDepositInput(contract, DEFAULT_BALANCE.add(BigInteger.ONE));
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test on maximum too-large amount.
        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositDirectDepositsDisabledCallerIsOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, false, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositDirectDepositsDisabledCallerNotOwner() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address owner = getNewExistentAccount(BigInteger.ZERO);
        Address contract = createTRScontract(owner, false, false, 1, BigInteger.ZERO, 0);

        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositCallerIsContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1, BigInteger.ZERO, 0);

        TRSuseContract trs = newTRSuseContract(contract);
        byte[] input = getDepositInput(contract, BigInteger.TWO);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositZero() {
        // Test zero deposit with zero balance.
        Address acct = getNewExistentAccount(BigInteger.ZERO);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ZERO);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(fetchAccountHasPositiveDepositBalance(contract, acct));

        // Test zero deposit with non-zero balance.
        acct = getNewExistentAccount(DEFAULT_BALANCE);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getDepositInput(contract, BigInteger.ZERO);
       res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertFalse(fetchAccountHasPositiveDepositBalance(contract, acct));
    }

    @Test
    public void testDepositOne() {
        // Test deposit with one balance.
        Address acct = getNewExistentAccount(BigInteger.ONE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(fetchAccountHasPositiveDepositBalance(contract, acct));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(BigInteger.ONE, fetchDepositBalance(contract, acct));

        // Test deposit with balance larger than one.
        acct = getNewExistentAccount(DEFAULT_BALANCE);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getDepositInput(contract, BigInteger.ONE);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(fetchAccountHasPositiveDepositBalance(contract, acct));
        assertEquals(DEFAULT_BALANCE.subtract(BigInteger.ONE), repo.getBalance(acct));
        assertEquals(BigInteger.ONE, fetchDepositBalance(contract, acct));
    }

    @Test
    public void testDepositFullBalance() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(fetchAccountHasPositiveDepositBalance(contract, acct));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(DEFAULT_BALANCE, fetchDepositBalance(contract, acct));

        // Test on max deposit amount.
        BigInteger max = getMaxOneTimeDeposit();
        acct = getNewExistentAccount(max);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getMaxDepositInput(contract);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(fetchAccountHasPositiveDepositBalance(contract, acct));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(max, fetchDepositBalance(contract, acct));
    }

    @Test
    public void testDepositMaxOneTimeAmount() {
        BigInteger max = getMaxOneTimeDeposit();
        Address acct = getNewExistentAccount(max.add(DEFAULT_BALANCE));
        TRSuseContract trs = newTRSuseContract(acct);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        byte[] input = getMaxDepositInput(contract);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(fetchAccountHasPositiveDepositBalance(contract, acct));
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct));
        assertEquals(max, fetchDepositBalance(contract, acct));
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
            ContractExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getCode());
            assertEquals(0, res.getNrgLeft());
            left = left.subtract(amt);
            depo = depo.add(amt);
        }

        assertEquals(left, repo.getBalance(acct));
        assertEquals(depo, fetchDepositBalance(contract, acct));
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
            ContractExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getCode());
            assertEquals(0, res.getNrgLeft());
            left = left.subtract(max);
            depo = depo.add(max);
        }

        assertEquals(left, repo.getBalance(acct));
        assertEquals(depo, fetchDepositBalance(contract, acct));
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

        assertEquals(acct1Depo, fetchDepositBalance(contract, acct1));
        assertEquals(acct2Depo, fetchDepositBalance(contract, acct2));
        assertEquals(acct3Depo, fetchDepositBalance(contract, acct3));
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(total, fetchDepositBalance(contract, acct));

        // Second we deposit 1 and then deposit 2**31 - 1 to overflow into next row.
        acct = getNewExistentAccount(total);
        amount = total.subtract(BigInteger.ONE);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        trs = newTRSuseContract(acct);
        input = getDepositInput(contract, BigInteger.ONE);
        trs.execute(input, COST);

        input = getDepositInput(contract, amount);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertEquals(total, fetchDepositBalance(contract, acct));
    }

    @Test
    public void testDepositNumRowsWhenAllRowsFull() {
        BigInteger total = BigInteger.TWO.pow(255);
        Address acct = getNewExistentAccount(total);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, total);
        trs.execute(input, COST);

        int rows = repo.getStorageValue(contract, new DoubleDataWord(acct.toBytes())).getData()[0] & 0x0F;
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

        int rows = repo.getStorageValue(contract, new DoubleDataWord(acct.toBytes())).getData()[0] & 0x0F;
        assertEquals(2, rows);
    }

    @Test
    public void testDepositRequiresMoreThan16StorageRows() {
        // It is computationally infeasible to ever hit the ceiling but just to get a test case in we
        // will cheat a little and set the 16 storage rows directly.
        //TODO
    }

    @Test
    public void testTotalDepositBalanceOneDepositor() {
        //TODO
    }

    @Test
    public void testTotalDepositBalanceMultipleDeposits() {
        //TODO
    }

    @Test
    public void testDepositWhileTRSisLocked() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testDepositWhileTRSisLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    // <----------------------------TRS DEPOSITOR LINKED LIST TESTS-------------------------------->

    @Test
    public void testLinkedListNoDepositors() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        assertNull(fetchLinkedListHead(contract));
    }

    @Test
    public void testLinkedListOneDepositor() {
        // Test one depositor makes one deposit.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        TRSuseContract trs = newTRSuseContract(acct);
        trs.execute(input, COST);
        assertEquals(acct, fetchLinkedListHead(contract));
        assertNull(fetchLinkedListNext(contract, acct));
        assertNull(fetchLinkedListPrev(contract, acct));

        // Test one depositor makes more than one deposit.
        trs.execute(input, COST);
        trs.execute(input, COST);
        assertEquals(acct, fetchLinkedListHead(contract));
        assertNull(fetchLinkedListNext(contract, acct));
        assertNull(fetchLinkedListPrev(contract, acct));
    }

    @Test
    public void testLinkedListTwoDepositors() {
        // Latest unique depositor is head of list.

        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, BigInteger.ONE);

        TRSuseContract trs = newTRSuseContract(acct);
        trs.execute(input, COST);

        trs = newTRSuseContract(acct2);
        trs.execute(input, COST);
        trs.execute(input, COST);

        trs = newTRSuseContract(acct);
        trs.execute(input, COST);

        // We should have:      null <- acct2 <-> acct1 -> null      with acct2 as head.
        assertEquals(acct2, fetchLinkedListHead(contract));
        assertEquals(acct, fetchLinkedListNext(contract, acct2));
        assertNull(fetchLinkedListPrev(contract, acct2));
        assertEquals(acct2, fetchLinkedListPrev(contract, acct));
        assertNull(fetchLinkedListNext(contract, acct));
    }

    @Test
    public void testLinkedListMultipleDepositors() {
        Address acct1, acct2, acct3, acct4;
        acct1 = getNewExistentAccount(DEFAULT_BALANCE);
        acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        acct4 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct1, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, BigInteger.ONE);

        newTRSuseContract(acct1).execute(input, COST);
        newTRSuseContract(acct4).execute(input, COST);
        newTRSuseContract(acct2).execute(input, COST);
        newTRSuseContract(acct4).execute(input, COST);
        newTRSuseContract(acct1).execute(input, COST);
        newTRSuseContract(acct3).execute(input, COST);
        newTRSuseContract(acct1).execute(input, COST);

        // We should have:      null <- acct3 <-> acct2 <-> acct4 <-> acct1 -> null     - acct3 head.
        assertEquals(acct3, fetchLinkedListHead(contract));
        assertNull(fetchLinkedListPrev(contract, acct3));
        assertEquals(acct2, fetchLinkedListNext(contract, acct3));
        assertEquals(acct3, fetchLinkedListPrev(contract, acct2));
        assertEquals(acct4, fetchLinkedListNext(contract, acct2));
        assertEquals(acct2, fetchLinkedListPrev(contract, acct4));
        assertEquals(acct1, fetchLinkedListNext(contract, acct4));
        assertEquals(acct4, fetchLinkedListPrev(contract, acct1));
        assertNull(fetchLinkedListNext(contract, acct1));
    }

}
