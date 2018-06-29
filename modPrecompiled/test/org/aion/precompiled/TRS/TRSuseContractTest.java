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
        byte[] maxDepo = new byte[129];
        for (int i = 1; i < 129; i++) {
            maxDepo[i] = (byte) 0xFF;
        }
        return new BigInteger(maxDepo);
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

    }

    @Test
    public void testDepositMaxTotalAmount() {

    }

    @Test
    public void testDepositMultipleDepositors() {

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
