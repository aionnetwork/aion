package org.aion.precompiled.TRS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        IDataWord val = repo.getStorageValue(contract, new DoubleDataWord(account.toBytes()));
        if (val == null) { return null; }
        int numRows = (val.getData()[0] & 0x0F);

        byte[] key = new byte[DoubleDataWord.BYTES];
        byte[] amt = new byte[(numRows * DoubleDataWord.BYTES) + 1];    // + 1 for unsigned.
        for (int i = 0; i < numRows; i++) {
            key[0] = (byte) (0xB0 | i);
            System.arraycopy(repo.getStorageValue(contract, new DoubleDataWord(key)).getData(),
                0, amt, (i * DoubleDataWord.BYTES) + 1, DoubleDataWord.BYTES);
        }
        return new BigInteger(amt);
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
        System.out.println(left);
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

        TRSuseContract trs1 = newTRSuseContract(acct1);
        TRSuseContract trs2 = newTRSuseContract(acct2);
        TRSuseContract trs3 = newTRSuseContract(acct3);
        Address contract1 = createTRScontract(acct1, false, true, 1, BigInteger.ZERO, 0);
        Address contract2 = createTRScontract(acct1, false, true, 1, BigInteger.ZERO, 0);
        Address contract3 = createTRScontract(acct1, false, true, 1, BigInteger.ZERO, 0);

        BigInteger depo1 = new BigInteger("123456");
        BigInteger depo2 = new BigInteger("4363123");
        BigInteger depo3 = new BigInteger("325");
        BigInteger depo4 = new BigInteger("8597455434");

        byte[] input1 = getDepositInput(contract1, depo1);
        byte[] input2 = getDepositInput(contract2, depo2);
        byte[] input3 = getDepositInput(contract3, depo3);
        byte[] input4 = getDepositInput(contract1, depo4);

        trs1.execute(input1, COST);
        trs2.execute(input2, COST);
        trs3.execute(input3, COST);
        trs1.execute(input4, COST);

        acct1Bal = acct1Bal.subtract(depo1).subtract(depo4);
        acct2Bal = acct2Bal.subtract(depo2);
        acct3Bal = acct3Bal.subtract(depo3);

        assertEquals(acct1Bal, repo.getBalance(acct1));
        assertEquals(acct2Bal, repo.getBalance(acct2));
        assertEquals(acct3Bal, repo.getBalance(acct3));

        assertEquals(depo1.add(depo4), fetchDepositBalance(contract1, acct1));
        assertEquals(depo2, fetchDepositBalance(contract2, acct2));
        assertEquals(depo3, fetchDepositBalance(contract3, acct3));
    }

    @Test
    public void testDepositMultipleTimesSomeOkSomeBad() {

    }

    @Test
    public void testDepositWhileTRSisLocked() {

    }

    @Test
    public void testDepositWhileTRSisLive() {

    }

    // <----------------------------TRS DEPOSITOR LINKED LIST TESTS-------------------------------->

    @Test
    public void testLinkedListNoDepositors() {

    }

    @Test
    public void testLinkedListOneDepositor() {

    }

    @Test
    public void testLinkedListTwoDepositors() {

    }

    @Test
    public void testLinkedListMultipleDepositors() {

    }

}
