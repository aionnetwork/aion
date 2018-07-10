package org.aion.precompiled.TRS;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the TRSqueryContract API.
 */
public class TRSqueryContractTest extends TRShelpers {
    private static final BigInteger DEFAULT_BALANCE = BigInteger.TEN;
    private ECKey senderKey;

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
        senderKey = null;
    }

    // <-----------------------------------HELPER METHODS BELOW------------------------------------>

    // Creates a new blockchain with numBlocks blocks and sets it to the blockchain field. This
    // method creates a new block every sleepDuration milliseconds.
    private void createBlockchain(int numBlocks, long sleepDuration) throws InterruptedException {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .build();

        StandaloneBlockchain bc = bundle.bc;
        senderKey = bundle.privateKeys.get(0);
        AionBlock previousBlock = bc.genesis;

        for (int i = 0; i < numBlocks; i++) {
            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
            if (sleepDuration > 0) { Thread.sleep(sleepDuration); }
        }

        blockchain = bc;
    }

    // Adds numBlocks more blocks to the blockchain every sleepDuration milliseconds.
    private void addBlocks(int numBlocks, long sleepDuration) throws InterruptedException {
        AionBlock previousBlock = blockchain.getBestBlock();
        for (int i = 0; i < numBlocks; i++) {
            previousBlock = createBundleAndCheck(((StandaloneBlockchain) blockchain), senderKey, previousBlock);
            if (sleepDuration > 0) { Thread.sleep(sleepDuration); }
        }
    }

    private static AionBlock createBundleAndCheck(StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        byte[] ZERO_BYTE = new byte[0];

        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 100 transactions per bundle
        for (int i = 0; i < 100; i++) {
            Address destAddr = new Address(HashUtil.h256(accountNonce.toByteArray()));
            AionTransaction sendTransaction = new AionTransaction(accountNonce.toByteArray(),
                destAddr, BigInteger.ONE.toByteArray(), ZERO_BYTE, 21000, 1);
            sendTransaction.sign(key);
            transactions.add(sendTransaction);
            accountNonce = accountNonce.add(BigInteger.ONE);
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);
        assertEquals(100, block.getTransactionsList().size());
        // clear the trie
        bc.getRepository().flush();

        ImportResult result = bc.tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }

    // <----------------------------------MISCELLANEOUS TESTS-------------------------------------->

    @Test(expected=NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSqueryContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        ContractExecutionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
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
        TRSqueryContract trs = newTRSqueryContract(addr);
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

    // <-----------------------------------IS STARTED TRS TESTS------------------------------------>

    @Test
    public void testIsLiveInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLiveNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLiveInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLiveInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------------IS LOCKED TRS TESTS------------------------------------>

    @Test
    public void testIsLockedInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x1;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x1;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsLockedNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLockedInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLockedInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------IS DIR DEPO ENABLED TRS TESTS-------------------------------->

    @Test
    public void testIsDepoEnabledInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x2;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x2;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testIsDepoEnabledNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsDirDepoEnabledInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsDirDepoEnabledInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenDisabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenEnabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1,
            BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <--------------------------------------PERIOD TESTS----------------------------------------->

    @Test
    public void testPeriodInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x3;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x3;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodOfNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodInput(acct);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodBeforeIsLive() throws InterruptedException {
        Address contract = createAndLockTRScontract(AION, true, true, 4,
            BigInteger.ZERO, 0);
        createBlockchain(1, TimeUnit.SECONDS.toMillis(2));

        byte[] input = getPeriodInput(contract);
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // Now we should be in period 1 forever.
        addBlocks(2, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        addBlocks(3, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // We can safely assume now the best block is in period 4.
        addBlocks(2, TimeUnit.SECONDS.toMillis(2));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        addBlocks(5, TimeUnit.SECONDS.toMillis(1));
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x4;
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodAtInput(acct, 0);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtNegativeBlockNumber() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true,
            1, BigInteger.ZERO, 0);
        byte[] input = getPeriodAtInput(contract, -1);
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.INTERNAL_ERROR, res.getCode());
        assertEquals(0, res.getNrgLeft());
    }

    @Test
    public void testPeriodAtBeforeIsLive() throws InterruptedException {
        int numBlocks = 5;

        Address contract = createAndLockTRScontract(AION, true, true, 4,
            BigInteger.ZERO, 0);
        createBlockchain(numBlocks, TimeUnit.SECONDS.toMillis(1));

        byte[] input = getPeriodAtInput(contract, numBlocks);
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtAfterIsLive() throws InterruptedException {
        Address contract = createLockedAndLiveTRScontract(AION, true, true,
            4, BigInteger.ZERO, 0);
        createBlockchain(2, TimeUnit.SECONDS.toMillis(3));

        byte[] input = getPeriodAtInput(contract, 1);
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));

        input = getPeriodAtInput(contract, 2);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertTrue(new BigInteger(res.getOutput()).compareTo(BigInteger.ONE) > 0);
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.valueOf(periods), new BigInteger(res.getOutput()));

        input = getPeriodAtInput(contract, numBlocks);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
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
        ContractExecutionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));

        input = getPeriodAtInput(contract, numBlocks);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getCode());
        assertEquals(0, res.getNrgLeft());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));
    }

}
