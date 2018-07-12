package org.aion.precompiled.TRS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
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

    // <-----------------------------------HELPER METHODS BELOW------------------------------------>

    // Returns a properly formatted byte array to be used as input for the refund operation, to
    // refund the maximum allowable amount.
    private byte[] getMaxRefundInput(Address contract, Address account) {
        byte[] input = new byte[193];
        input[0] = 0x5;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(account.toBytes(), 0, input, 33, Address.ADDRESS_LEN);
        for (int i = 65; i < 193; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation, to
    // deposit the maximum allowable amount.
    private byte[] getMaxDepositInput(Address contract) {
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        for (int i = 33; i < 161; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a TRS contract address that has numDepositors depositors in it (owner does not deposit)
    // each with DEFAULT_BALANCE deposit balance.
    private Address getContractMultipleDepositors(int numDepositors, Address owner, boolean isTest,
        boolean isDirectDeposit, int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        for (int i = 0; i < numDepositors; i++) {
            Address acct = getNewExistentAccount(DEFAULT_BALANCE);
            if (!newTRSuseContract(acct).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
                fail("Depositor #" + i + " failed to deposit!");
            }
        }
        return contract;
    }

    // Returns the maximum amount that can be deposited in a single deposit call.
    private BigInteger getMaxOneTimeDeposit() {
        return BigInteger.TWO.pow(1024).subtract(BigInteger.ONE);
    }

    // Returns the maximum amount that a single account can deposit into a TRS contract.
    private BigInteger getMaxTotalDeposit() {
        return BigInteger.TWO.pow(4096).subtract(BigInteger.ONE);
    }

    // Returns the deposit balance of account in the TRS contract contract.
    private BigInteger getDepositBalance(TRSuseContract trs, Address contract, Address account) {
        return trs.getDepositBalance(contract, account);
    }

    // Returns true only if account is a valid account in contract.
    private boolean accountIsValid(TRSuseContract trs, Address contract, Address account) {
        try {
            return AbstractTRS.accountIsValid(trs.getListNextBytes(contract, account));
        } catch (Exception e) {
            // Since we possibly call on a non-existent account.
            return false;
        }
    }

    // Returns true only if account is eligible to use the special one-off withdrawal event.
    private boolean accountIsEligibleForSpecial(TRSuseContract trs, Address contract, Address account) {
        return trs.accountIsEligibleForSpecial(contract, account);
    }

    // Returns the last period in which account made a withdrawal or -1 if bad contract or account.
    private int getAccountLastWithdrawalPeriod(AbstractTRS trs, Address contract, Address account) {
        return trs.getAccountLastWithdrawalPeriod(contract, account);
    }

    /**
     * Creates a contract with AION as the owner and has numDepositors deposit deposits amount
     * each. A bonus deposit of bonus is made. Then the contract is locked and made live.
     *
     * The contract is set to be in testing mode. It has a total of periods periods and percentage
     * is the percent of the total owings that an account can withdraw in the special one-off event.
     *
     * The owner does not deposit.
     *
     * @param numDepositors The number of depositors, excluding owner, who deposit into contract.
     * @param deposits The amount each depositor deposits.
     * @param bonus The bonus amount.
     * @param periods The number of periods the contract has.
     * @param percentage The percent of total owings that can be claimed in special event.
     * @return the address of the contract.
     */
    private Address setupContract(int numDepositors, BigInteger deposits, BigInteger bonus,
        int periods, BigDecimal percentage) {

        int precision = percentage.scale();
        BigInteger percent = percentage.movePointRight(precision).toBigInteger();
        Address contract = createTRScontract(AION, true, true, periods,
            percent, precision);

        assertEquals(percentage, getPercentage(newTRSownerContract(AION), contract));
        assertEquals(periods, getPeriods(newTRSownerContract(AION), contract));

        byte[] input = getDepositInput(contract, deposits);
        for (int i = 0; i < numDepositors; i++) {
            Address acc = getNewExistentAccount(deposits);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
        }
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, AION);
        return contract;
    }

    // Grabs the current period contract is in as a BigInteger.
    private BigInteger grabCurrentPeriod(AbstractTRS trs, Address contract) {
        return BigInteger.valueOf(getContractCurrentPeriod(trs, contract));
    }

    // Grabs the amount owed to account if the following params are true.
    private BigInteger grabOwings(BigDecimal accBalance, BigDecimal contractBalance, BigDecimal bonus) {
        BigDecimal fraction = accBalance.divide(contractBalance, 18, RoundingMode.HALF_DOWN);
        BigDecimal share = bonus.multiply(fraction);
        return share.toBigInteger().add(accBalance.toBigInteger());
    }

    // Grabs the amount an account can withdraw in special event according to these params.
    private BigInteger grabSpecialAmount(BigDecimal accBalance, BigDecimal contractBalance,
        BigDecimal bonus, BigDecimal percent) {

        BigDecimal owings = new BigDecimal(grabOwings(accBalance, contractBalance, bonus));
        return (owings.multiply(percent.movePointLeft(2))).toBigInteger();
    }

    // Grabs the amount an account can withdraw regularly, not in special event, according to params.
    private BigInteger grabWithdrawAmt(BigInteger owings, BigInteger specialAmt, int periods) {
        BigDecimal periodsBI = new BigDecimal(BigInteger.valueOf(periods));
        BigDecimal owingWithoutSpec = new BigDecimal(owings.subtract(specialAmt));
        BigDecimal res = owingWithoutSpec.divide(periodsBI, 18, RoundingMode.HALF_DOWN);
        return res.toBigInteger();
    }

    /**
     * Returns the amount an account is expected to receive from a first withdrawal from a contract
     * given that the params are true of the contract and the caller.
     *
     * This method is unreliable if the first withdraw is performed in the last period. There are
     * roundoff errors that accrue and cause the non-final withdrawal amounts to be round down and
     * their sum may be less than the total owed. The contract handles the final period specially to
     * ensure all funds owed are paid out.
     *
     * @param trs An AbstractTRS instance.
     * @param contract The contract in question.
     * @param deposits The amount the caller deposited.
     * @param total The total amount of deposits in the contract.
     * @param bonus The bonus balance in the contract.
     * @param percent The percentage of total owings the caller is eligible to receive in special event.
     * @param periods The number of periods the contract has.
     * @return the expected amount to withdraw on a first call to the contract.
     */
    private BigInteger expectedAmtFirstWithdraw(AbstractTRS trs, Address contract, BigInteger deposits,
        BigInteger total, BigInteger bonus, BigDecimal percent, int periods) {

        BigInteger currPeriod = grabCurrentPeriod(trs, contract);
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger expectedSpecial = grabSpecialAmount(new BigDecimal(deposits), new BigDecimal(total),
            new BigDecimal(bonus), percent);
        BigInteger expectedWithdraw = currPeriod.multiply(grabWithdrawAmt(owings, expectedSpecial, periods));
        return expectedWithdraw.add(expectedSpecial);
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

    @Test
    public void testInvalidOperation() {
        //TODO - need all ops implemented first
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
        assertEquals(BigInteger.TWO, getTotalBalance(trs, contract));
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
        assertFalse(getDepositBalance(trs, contract, acct).compareTo(BigInteger.ZERO) > 0);
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));

        // Test zero deposit with non-zero balance.
        acct = getNewExistentAccount(DEFAULT_BALANCE);
        trs = newTRSuseContract(acct);
        contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);

        input = getDepositInput(contract, BigInteger.ZERO);
       res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
            ContractExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getCode());
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
            ContractExecutionResult res = trs.execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
    public void testDepositRequiresMoreThan16StorageRows() {
        // It is computationally infeasible to ever hit the ceiling but just to get a test case in we
        // will cheat a little and set the 16 storage rows directly.
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
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testWithdrawContractNotLockedOrLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getCode());

        input = getWithdrawInput(contract);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(DEFAULT_BALANCE, getDepositBalance(newTRSuseContract(acct), contract, acct));
    }

    @Test
    public void testWithdrawContractLockedNotLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getCode());

        // Lock the contract.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(acct).execute(input, COST).getCode());

        input = getWithdrawInput(contract);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        // Test that locking changes nothing.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(owner).execute(input, COST).getCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));
    }

    @Test
    public void testLastWithdrawalPeriodOnceLive() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acc = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());

        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(owner).execute(input, COST).getCode());
        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(owner).execute(input, COST).getCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));
    }

    @Test
    public void testLastWithdrawalPeriodComingAndGoing() {
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address acc = getNewExistentAccount(BigInteger.ONE);
        Address contract = createTRScontract(owner, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
        assertEquals(0, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        input = getRefundInput(contract, acc, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(owner).execute(input, COST).getCode());
        assertEquals(-1, getAccountLastWithdrawalPeriod(newTRSuseContract(owner), contract, acc));

        input = getDepositInput(contract, BigInteger.ONE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(AION).execute(input, COST).getCode());
        assertEquals(expectedBalAfterDepo, repo.getBalance(AION));
        lockAndStartContract(contract, AION);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));

        input = getWithdrawInput(contract);
        TRSuseContract trs = newTRSuseContract(AION);
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, DEFAULT_BALANCE,
            DEFAULT_BALANCE, BigInteger.ZERO, BigDecimal.ZERO, periods);
        BigInteger expectedBal = expectedBalAfterDepo.add(expectedAmt);

        // Try to keep withdrawing...
        for (int i = 0; i < 5; i++) {
            assertEquals(ResultCode.INTERNAL_ERROR, trs.execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> depositors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : depositors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }

        // Now put the contract in its final period and withdraw. All accounts should have their
        // origial balance back.
        addBlocks(1, TimeUnit.SECONDS.toMillis(3));
        assertEquals(periods, getContractCurrentPeriod(trs, contract));
        for (Address acc : depositors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        BigInteger expectedAmt = grabSpecialAmount(new BigDecimal(deposits), new BigDecimal(total),
            new BigDecimal(bonus), percent);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(expectedAmt, repo.getBalance(acc));
        }

        // Let's try and withdraw again, now we should not be able to.
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        assertTrue(grabCurrentPeriod(trs, contract).compareTo(BigInteger.ONE) > 0);
        BigInteger expectedAmt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        assertEquals(BigInteger.valueOf(periods), grabCurrentPeriod(trs, contract));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Try to withdraw again from the final period.
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getCode());
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

        AbstractTRS trs = newTRSownerContract(AION);
        createBlockchain(0, 0);

        boolean isAccNotDone;
        boolean isDone = false;
        byte[] input = getWithdrawInput(contract);
        while (!isDone) {
            isAccNotDone = false;

            Set<Address> contributors = getAllDepositors(trs, contract);
            for (Address acc : contributors) {
                if (newTRSuseContract(acc).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
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
        AbstractTRS trs = newTRSownerContract(AION);

        // We are in last period so we expect to withdraw our total owings.
        BigInteger currPeriod = grabCurrentPeriod(trs, contract);
        assertTrue(currPeriod.compareTo(BigInteger.valueOf(periods)) == 0);
        BigInteger accOwed = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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

        AbstractTRS trs = newTRSownerContract(AION);
        createBlockchain(0, 0);

        boolean isAccNotDone;
        boolean isDone = false;
        while (!isDone) {
            isAccNotDone = false;

            Set<Address> contributors = getAllDepositors(trs, contract);
            byte[] input = getWithdrawInput(contract);
            for (Address acc : contributors) {
                if (newTRSuseContract(acc).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
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
    public void testWithdrawLargeWealthGap() {
        //TODO
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
        AbstractTRS trs = newTRSownerContract(AION);
        assertTrue(grabCurrentPeriod(trs, contract).compareTo(BigInteger.valueOf(periods)) == 0);
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(owings, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawSmallDepositsLargeBonus() {
        //TODO
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
        AbstractTRS trs = newTRSownerContract(AION);
        assertEquals(0, grabCurrentPeriod(trs, contract).intValue());
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Now move into a non-final period and ensure no more withdrawals can be made.
        addBlocks(1, TimeUnit.SECONDS.toMillis(1));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(owings, repo.getBalance(acc));
        }

        // Now move into a final period and ensure no more withdrawals can be made.
        addBlocks(1, TimeUnit.SECONDS.toMillis(7));
        assertEquals(BigInteger.valueOf(periods), grabCurrentPeriod(trs, contract));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getCode());
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
        AbstractTRS trs = newTRSownerContract(AION);
        assertEquals(0, grabCurrentPeriod(trs, contract).intValue());
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        assertTrue(amt.compareTo(owings) < 0);

        Set<Address> contributors = getAllDepositors(trs, contract);
        byte[] input = getWithdrawInput(contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(amt, repo.getBalance(acc));
        }

        // Now move into a non-final period and ensure all withdrawals fail (no positive amount to claim).
        addBlocks(1, TimeUnit.SECONDS.toMillis(1));
        for (Address acc : contributors) {
            assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(amt, repo.getBalance(acc));
        }

        // Now move into a final period and ensure we can make a withdrawal for our last coin.
        addBlocks(1, TimeUnit.SECONDS.toMillis(7));
        assertEquals(BigInteger.valueOf(periods), grabCurrentPeriod(trs, contract));
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
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
        BigInteger total = deposits.multiply(BigInteger.valueOf(depositors));
        Address contract = setupContract(depositors, deposits, bonus, periods, percent);

        // Make a large bonus deposit while contract is live.
        repo.addBalance(contract, new BigInteger("876523876532534634"));

        // Withdraw all funds and ensure it equals deposits, meaning the later bonus was ignored.
        createBlockchain(1, TimeUnit.SECONDS.toMillis(3));
        byte[] input = getWithdrawInput(contract);
        AbstractTRS trs = newTRSownerContract(AION);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getCode());
            assertEquals(deposits, repo.getBalance(acc));
        }
    }

    @Test
    public void testWithdrawContractHasOnePeriod() {
        //TODO
    }

    @Test
    public void testWithdrawSpecialPercentage18DecimalsPrecise() {
        //TODO
    }

    @Test
    public void testWithdrawRealTimeAsBlocksAreMade() {
        //TODO
    }

    // <--------------------------------------REFUND TRS TESTS------------------------------------->

    @Test
    public void testRefundInputTooShort() {
        // Test maximum too-short size.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[192];
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test minimum too-short size.
        input = new byte[1];
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundInputTooLarge() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = new byte[194];
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundBadTRScontract() {
        // Test TRS address that looks like regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getRefundInput(contract, acct, BigInteger.ZERO);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Test TRS address with TRS prefix, so it looks legit.
        byte[] addr = ECKeyFac.inst().create().getAddress();
        addr[0] = (byte) 0xC0;
        contract = new Address(addr);
        tempAddrs.add(contract);
        input = getRefundInput(contract, acct, BigInteger.ZERO);
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());

        // acct2 calls refund but owner is acct
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());

        // Have others deposit but not acct2 and try again ... should be same result.
        Address acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct4 = getNewExistentAccount(DEFAULT_BALANCE);

        input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct4).execute(input, COST).getCode());

        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = newTRSuseContract(acct2).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        // Now lock the contract.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(acct).execute(input, COST).getCode());

        // Now have contract owner try to refund acct2.
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        // Now lock the contract and make it live.
        input = getLockInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(acct).execute(input, COST).getCode());
        input = getStartInput(contract);
        assertEquals(ResultCode.SUCCESS, newTRSownerContract(acct).execute(input, COST).getCode());

        // Now have contract owner try to refund acct2.
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        // Now have contract owner try to refund acct2 for more than acct2 has deposited.
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE.add(BigInteger.ONE));
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertTrue(accountIsValid(trs, contract, acct2));

        // Now have contract owner try to refund acct2 for exactly what acct2 has deposited.
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertEquals(max, getDepositBalance(trs, contract, acct));
        assertEquals(max, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct));
        assertTrue(accountIsValid(trs, contract, acct));

        input = getMaxRefundInput(contract, acct);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertEquals(depositAmt, getDepositBalance(trs, contract, acct));
        assertEquals(depositAmt, getTotalBalance(trs, contract));
        assertEquals(max.subtract(depositAmt), repo.getBalance(acct));
        assertTrue(accountIsValid(trs, contract, acct));

        BigInteger diff = new BigInteger("23478523");
        input = getRefundInput(contract, acct, depositAmt.subtract(diff));
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        input = getDepositInput(contract, funds2);
        trs = newTRSuseContract(acct2);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        input = getDepositInput(contract, funds3);
        trs = newTRSuseContract(acct3);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getCode());
        input = getRefundInput(contract, acct2, funds2.subtract(diff2));
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getCode());
        input = getRefundInput(contract, acct3, funds3);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct1).execute(input, COST).getCode());

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
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertEquals(DEFAULT_BALANCE, getDepositBalance(trs, contract, acct2));
        assertEquals(DEFAULT_BALANCE, getTotalBalance(trs, contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(acct2));
        assertTrue(accountIsValid(trs, contract, acct2));

        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());

        assertEquals(BigInteger.ZERO, getDepositBalance(trs, contract, acct2));
        assertEquals(BigInteger.ZERO, getTotalBalance(trs, contract));
        assertEquals(DEFAULT_BALANCE, repo.getBalance(acct2));
        assertFalse(accountIsValid(trs, contract, acct2));

        // Now try to refund acct2 again...
        input = getRefundInput(contract, acct2, BigInteger.ONE);
        res = newTRSuseContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = trs.execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testRefundZeroForInvalidAccount() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getCode());

        // Acct2 is now marked invalid.
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        assertEquals(ResultCode.INTERNAL_ERROR, newTRSuseContract(acct).execute(input, COST).getCode());
    }

    @Test
    public void testRefundZeroForValidAccount() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        // Now try to refund nothing, acct2 exists in the contract.
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct).execute(input, COST).getCode());

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
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        long diff = 47835;
        input = getRefundInput(contract, acct2, BigInteger.ZERO);
        ContractExecutionResult res = newTRSuseContract(acct).execute(input, COST + diff);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(diff, res.getNrgLeft());
    }

    // <----------------------------TRS DEPOSITOR LINKED LIST TESTS-------------------------------->

    @Test
    public void testLinkedListNoDepositors() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        TRSuseContract trs = newTRSuseContract(acct);
        assertNull(getLinkedListHead(trs, contract));
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
        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct));

        // Test one depositor makes more than one deposit.
        trs.execute(input, COST);
        trs.execute(input, COST);
        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct));
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

        // We should have:      null <- acct2 <-> acct -> null      with acct2 as head.
        assertEquals(acct2, getLinkedListHead(trs, contract));
        assertEquals(acct, getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListPrev(trs, contract, acct2));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListNext(trs, contract, acct));
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
        TRSuseContract trs = newTRSuseContract(acct1);
        assertEquals(acct3, getLinkedListHead(trs, contract));
        assertNull(getLinkedListPrev(trs, contract, acct3));
        assertEquals(acct2, getLinkedListNext(trs, contract, acct3));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct2));
        assertEquals(acct4, getLinkedListNext(trs, contract, acct2));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct4));
        assertEquals(acct1, getLinkedListNext(trs, contract, acct4));
        assertEquals(acct4, getLinkedListPrev(trs, contract, acct1));
        assertNull(getLinkedListNext(trs, contract, acct1));
    }

    @Test
    public void testRemoveHeadOfListWithHeadOnly() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct));

        input = getRefundInput(contract, acct, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertFalse(accountIsValid(trs, contract, acct));
        assertNull(getLinkedListHead(trs, contract));
    }

    @Test
    public void testRemoveHeadOfListWithHeadAndNextOnly() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Now we have:     null <- acct2 <-> acct -> null      with acct2 as head.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        assertEquals(acct2, getLinkedListHead(trs, contract));
        assertEquals(acct, getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct2));

        // We remove acct2, the head. Should have:      null <- acct -> null
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertFalse(accountIsValid(trs, contract, acct2));
        assertEquals(acct, getLinkedListHead(trs, contract));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct));
    }

    @Test
    public void testRemoveHeadOfLargerList() {
        int listSize = 10;
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address contract = getContractMultipleDepositors(listSize, owner, false,
            true, 1, BigInteger.ZERO, 0);

        // We have a linked list with 10 depositors. Remove the head.
        TRSuseContract trs = newTRSuseContract(owner);
        Address head = getLinkedListHead(trs, contract);
        Address next = getLinkedListNext(trs, contract, head);
        assertNull(getLinkedListPrev(trs, contract, head));
        assertEquals(head, getLinkedListPrev(trs, contract, next));
        byte[] input = getRefundInput(contract, head, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        // We verify next is the new head, its prev is null and that we can advance 8 times before
        // hitting the end of the list.
        assertEquals(next, getLinkedListHead(trs, contract));
        assertNull(getLinkedListPrev(trs, contract, next));

        // We also make sure each address in the list is unique.
        Set<Address> addressesInList = new HashSet<>();
        for (int i = 0; i < listSize - 1; i++) {
            if (i == listSize - 2) {
                assertNull(getLinkedListNext(trs, contract, next));
            } else {
                next = getLinkedListNext(trs, contract, next);
                assertNotNull(next);
                assertFalse(addressesInList.contains(next));
                addressesInList.add(next);
            }
        }
    }

    @Test
    public void testRemoveTailOfSizeTwoList() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Now we have:     null <- acct2 <-> acct -> null      with acct2 as head, acct as tail.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());

        assertEquals(acct2, getLinkedListHead(trs, contract));
        assertEquals(acct, getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct2));

        // We remove acct, the tail. Should have:      null <- acct2 -> null
        input = getRefundInput(contract, acct, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertFalse(accountIsValid(trs, contract, acct));
        assertEquals(acct2, getLinkedListHead(trs, contract));
        assertNull(getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListPrev(trs, contract, acct2));
    }

    @Test
    public void testRemoveTailOfLargerList() {
        int listSize = 10;
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address contract = getContractMultipleDepositors(listSize, owner, false,
            true, 1, BigInteger.ZERO, 0);

        // We have a linked list with 10 depositors. First find the tail. Ensure each address is unique too.
        TRSuseContract trs = newTRSuseContract(owner);
        Address next = getLinkedListHead(trs, contract);
        Address head = new Address(next.toBytes());
        Set<Address> addressesInList = new HashSet<>();
        for (int i = 0; i < listSize; i++) {
            if (i == listSize - 1) {
                assertNull(getLinkedListNext(trs, contract, next));
            } else {
                next = getLinkedListNext(trs, contract, next);
                assertNotNull(next);
                assertFalse(addressesInList.contains(next));
                addressesInList.add(next);
            }
        }

        // Now next should be the tail. Remove it. Iterate over list again.
        byte[] input = getRefundInput(contract, next, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        assertFalse(accountIsValid(trs, contract, next));

        assertEquals(head, getLinkedListHead(trs, contract));
        for (int i = 0; i < listSize - 1; i++) {
            if (i == listSize - 2) {
                assertNull(getLinkedListNext(trs, contract, head));
            } else {
                head = getLinkedListNext(trs, contract, head);
                assertNotNull(head);
                assertTrue(addressesInList.contains(head));
                assertNotEquals(next, head);
            }
        }
    }

    @Test
    public void testRemoveInteriorOfSizeThreeList() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct2 = getNewExistentAccount(DEFAULT_BALANCE);
        Address acct3 = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);

        // Now we have:     null <- acct3 <-> acct2 <-> acct -> null      with acct3 as head.
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        TRSuseContract trs = newTRSuseContract(acct);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct2).execute(input, COST).getCode());
        assertEquals(ResultCode.SUCCESS, newTRSuseContract(acct3).execute(input, COST).getCode());

        assertEquals(acct3, getLinkedListHead(trs, contract));
        assertEquals(acct2, getLinkedListNext(trs, contract, acct3));
        assertEquals(acct, getLinkedListNext(trs, contract, acct2));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertEquals(acct2, getLinkedListPrev(trs, contract, acct));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct2));
        assertNull(getLinkedListPrev(trs, contract, acct3));

        // We remove acct2. Should have:      null <- acct3 <-> acct -> null    with acct3 as head.
        input = getRefundInput(contract, acct2, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());

        assertFalse(accountIsValid(trs, contract, acct2));
        assertEquals(acct3, getLinkedListHead(trs, contract));
        assertEquals(acct, getLinkedListNext(trs, contract, acct3));
        assertNull(getLinkedListNext(trs, contract, acct));
        assertEquals(acct3, getLinkedListPrev(trs, contract, acct));
        assertNull(getLinkedListPrev(trs, contract, acct3));
    }

    @Test
    public void testRemoveInteriorOfLargerList() {
        int listSize = 10;
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address contract = getContractMultipleDepositors(listSize, owner, false,
            true, 1, BigInteger.ZERO, 0);

        // We have a linked list with 10 depositors. Grab the 5th in line. Ensure each address is unique too.
        TRSuseContract trs = newTRSuseContract(owner);
        Address next = getLinkedListHead(trs, contract);
        Address head = new Address(next.toBytes());
        Address mid = null;
        Set<Address> addressesInList = new HashSet<>();
        for (int i = 0; i < listSize; i++) {
            if (i == listSize - 1) {
                assertNull(getLinkedListNext(trs, contract, next));
            } else if (i == 4) {
                next = getLinkedListNext(trs, contract, next);
                assertNotNull(next);
                assertFalse(addressesInList.contains(next));
                addressesInList.add(next);
                mid = new Address(next.toBytes());
            } else {
                next = getLinkedListNext(trs, contract, next);
                assertNotNull(next);
                assertFalse(addressesInList.contains(next));
                addressesInList.add(next);
            }
        }

        // Remove mid. Iterate over list again.
        byte[] input = getRefundInput(contract, mid, DEFAULT_BALANCE);
        assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
        assertFalse(accountIsValid(trs, contract, mid));

        assertEquals(head, getLinkedListHead(trs, contract));
        for (int i = 0; i < listSize - 1; i++) {
            if (i == listSize - 2) {
                assertNull(getLinkedListNext(trs, contract, head));
            } else {
                head = getLinkedListNext(trs, contract, head);
                assertNotNull(head);
                assertTrue(addressesInList.contains(head));
                assertNotEquals(mid, head);
            }
        }
    }

    @Test
    public void testMultipleListRemovals() {
        int listSize = 10;
        Address owner = getNewExistentAccount(BigInteger.ONE);
        Address contract = getContractMultipleDepositors(listSize, owner, false,
            true, 1, BigInteger.ZERO, 0);

        // We have a linked list with 10 depositors. Ensure each address is unique. Grab every other
        // address to remove.
        TRSuseContract trs = newTRSuseContract(owner);
        Address next = getLinkedListHead(trs, contract);
        Set<Address> removals = new HashSet<>();
        Set<Address> addressesInList = new HashSet<>();
        for (int i = 0; i < listSize; i++) {
            if (i == listSize - 1) {
                assertNull(getLinkedListNext(trs, contract, next));
            } else {
                next = getLinkedListNext(trs, contract, next);
                assertNotNull(next);
                assertFalse(addressesInList.contains(next));
                addressesInList.add(next);
                if (i % 2 == 0) {
                    removals.add(next);
                }
            }
        }

        // Remove all accts in removals. Iterate over list again.
        for (Address rm : removals) {
            byte[] input = getRefundInput(contract, rm, DEFAULT_BALANCE);
            assertEquals(ResultCode.SUCCESS, trs.execute(input, COST).getCode());
            assertFalse(accountIsValid(trs, contract, rm));
        }

        Address head = getLinkedListHead(trs, contract);
        assertFalse(removals.contains(head));
        for (int i = 0; i < listSize / 2; i++) {
            if (i == (listSize / 2) - 1) {
                assertNull(getLinkedListNext(trs, contract, head));
            } else {
                head = getLinkedListNext(trs, contract, head);
                assertNotNull(head);
                assertTrue(addressesInList.contains(head));
                assertFalse(removals.contains(head));
            }
        }
    }

}
