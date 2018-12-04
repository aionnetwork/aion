/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

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
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.contracts.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests the TRSqueryContract API. */
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

    @Test(expected = NullPointerException.class)
    public void testCreateNullCaller() {
        newTRSqueryContract(null);
    }

    @Test
    public void testCreateNullInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        TransactionResult res = trs.execute(null, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testCreateEmptyInput() {
        TRSqueryContract trs = newTRSqueryContract(getNewExistentAccount(BigInteger.ZERO));
        TransactionResult res = trs.execute(ByteUtil.EMPTY_BYTE_ARRAY, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testInsufficientNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        TransactionResult res;
        for (int i = 0; i <= MAX_OP; i++) {
            res = trs.execute(input, COST - 1);
            assertEquals(ResultCode.OUT_OF_ENERGY, res.getResultCode());
            assertEquals(0, res.getEnergyRemaining());
        }
    }

    @Test
    public void testTooMuchNrg() {
        Address addr = getNewExistentAccount(BigInteger.ZERO);
        TRSqueryContract trs = newTRSqueryContract(addr);
        byte[] input = getDepositInput(addr, BigInteger.ZERO);
        TransactionResult res;
        for (int i = 0; i <= MAX_OP; i++) {
            res = trs.execute(input, StatefulPrecompiledContract.TX_NRG_MAX + 1);
            assertEquals(ResultCode.INVALID_ENERGY_LIMIT, res.getResultCode());
            assertEquals(0, res.getEnergyRemaining());
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
                assertEquals(ResultCode.FAILURE, trs.execute(input, COST).getResultCode());
            }
        }
    }

    // <-----------------------------------IS STARTED TRS TESTS------------------------------------>

    @Test
    public void testIsLiveInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsLiveInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsLiveNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLiveInput(acct);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLiveInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLiveOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLiveInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------------IS LOCKED TRS TESTS------------------------------------>

    @Test
    public void testIsLockedInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x1;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsLockedInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x1;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsLockedNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsLockedInput(acct);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsLockedInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnUnlockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLockedContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createAndLockTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    @Test
    public void testIsLockedOnLiveContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsLockedInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <------------------------------IS DIR DEPO ENABLED TRS TESTS-------------------------------->

    @Test
    public void testIsDepoEnabledInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x2;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsDepoEnabledInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x2;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testIsDepoEnabledNonExistentContract() {
        // Test on a contract address that is just a regular account address.
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getIsDirDepoEnabledInput(acct);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());

        // Test on a contract address that looks real, uses TRS prefix.
        byte[] phony = acct.toBytes();
        phony[0] = (byte) 0xC0;
        input = getIsDirDepoEnabledInput(new Address(phony));
        res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenDisabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, false, 1, BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getFalseContractOutput(), res.getOutput());
    }

    @Test
    public void testIsDepoEnabledWhenEnabled() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getIsDirDepoEnabledInput(contract);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertArrayEquals(getTrueContractOutput(), res.getOutput());
    }

    // <--------------------------------------PERIOD TESTS----------------------------------------->

    @Test
    public void testPeriodInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[32];
        input[0] = 0x3;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x3;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodOfNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodInput(acct);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodBeforeIsLive() {
        Address contract = createAndLockTRScontract(AION, true, true, 4, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        mockBlockchain(getContractTimestamp(trs, contract) + 2);

        byte[] input = getPeriodInput(contract);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodOnEmptyBlockchain() {
        Address contract = createLockedAndLiveTRScontract(AION, true, true, 4, BigInteger.ZERO, 0);
        mockBlockchain(0);

        // We are using a timestamp of zero and therefore we are in period 0 at this point.
        byte[] input = getPeriodInput(contract);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodContractDeployedAfterBestBlock() {
        mockBlockchain(0);
        Address contract = createLockedAndLiveTRScontract(AION, true, true, 5, BigInteger.ZERO, 0);

        // In period zero.
        byte[] input = getPeriodInput(contract);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodWhenPeriodsIsOne() {
        mockBlockchain(0);
        Address contract = createLockedAndLiveTRScontract(AION, true, true, 1, BigInteger.ZERO, 0);

        // Period is zero.
        byte[] input = getPeriodInput(contract);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // Now we should be in period 1 forever.
        AbstractTRS trs = newTRSqueryContract(AION);
        mockBlockchain(getContractTimestamp(trs, contract) + 2);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        mockBlockchain(getContractTimestamp(trs, contract) + 5);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period3 = new BigInteger(res.getOutput()).intValue();

        assertEquals(0, period1);
        assertTrue(period2 > period1);
        assertEquals(1, period2);
        assertEquals(period3, period2);
    }

    @Test
    public void testPeriodAtDifferentMoments() {
        int numPeriods = 4;
        Address contract =
                createLockedAndLiveTRScontract(AION, true, true, numPeriods, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        mockBlockchain(getContractTimestamp(trs, contract));

        // Not yet in last period.
        byte[] input = getPeriodInput(contract);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period1 = new BigInteger(res.getOutput()).intValue();

        // We are in last period now.
        mockBlockchain(getContractTimestamp(trs, contract) + 4);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        int period2 = new BigInteger(res.getOutput()).intValue();

        mockBlockchain(getContractTimestamp(trs, contract) + 9);
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
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodAtInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = new byte[34];
        input[0] = 0x4;
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodAtNonExistentContract() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getPeriodAtInput(acct, 0);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodAtNegativeBlockNumber() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        byte[] input = getPeriodAtInput(contract, -1);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodAtBlockTooLargeAndDoesNotExist() {
        int numBlocks = 1;

        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createLockedAndLiveTRScontract(acct, false, true, 1, BigInteger.ZERO, 0);
        mockBlockchain(0);

        byte[] input = getPeriodAtInput(contract, numBlocks + 1);
        TransactionResult res = newTRSqueryContract(acct).execute(input, COST);
        assertEquals(ResultCode.FAILURE, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
    }

    @Test
    public void testPeriodAtBeforeIsLive() {
        int numBlocks = 5;

        Address contract = createAndLockTRScontract(AION, true, true, 4, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        mockBlockchain(getContractTimestamp(trs, contract) + 5);

        byte[] input = getPeriodAtInput(contract, numBlocks);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(BigInteger.ZERO, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtAfterIsLive() {
        Address contract = createLockedAndLiveTRScontract(AION, true, true, 4, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        long blockNum = 1;
        long timestamp = getContractTimestamp(trs, contract) + 1;
        mockBlockchain(timestamp);
        mockBlockchainBlock(blockNum, timestamp);

        BigInteger expectedPeriod = getPeriodAt(newTRSstateContract(AION), contract, blockNum);
        byte[] input = getPeriodAtInput(contract, blockNum);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(expectedPeriod, new BigInteger(res.getOutput()));

        blockNum = 2;
        timestamp++;
        mockBlockchainBlock(blockNum, timestamp);
        expectedPeriod = getPeriodAt(newTRSstateContract(AION), contract, blockNum);
        input = getPeriodAtInput(contract, blockNum);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(expectedPeriod, new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtMaxPeriods() {
        int periods = 5;
        int numBlocks = 10;

        Address contract =
                createLockedAndLiveTRScontract(AION, true, true, periods, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        long blockNum = periods;
        long timestamp = getContractTimestamp(trs, contract) + 5;
        mockBlockchain(getContractTimestamp(trs, contract) + 20);
        mockBlockchainBlock(blockNum, timestamp);

        // Since we create a block every 2 seconds and a period lasts 1 seconds we should be more
        // than safe in assuming block number periods is in the last period.
        byte[] input = getPeriodAtInput(contract, blockNum);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(BigInteger.valueOf(periods), new BigInteger(res.getOutput()));

        blockNum = numBlocks;
        timestamp += 15;
        mockBlockchainBlock(blockNum, timestamp);
        input = getPeriodAtInput(contract, blockNum);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(BigInteger.valueOf(periods), new BigInteger(res.getOutput()));
    }

    @Test
    public void testPeriodAtWhenPeriodsIsOne() {
        int numBlocks = 5;

        Address contract = createLockedAndLiveTRScontract(AION, true, true, 1, BigInteger.ZERO, 0);
        AbstractTRS trs = newTRSqueryContract(AION);
        long blockNum = 1;
        long timestamp = getContractTimestamp(trs, contract) + 1;
        mockBlockchain(getContractTimestamp(trs, contract) + 5);
        mockBlockchainBlock(blockNum, timestamp);

        byte[] input = getPeriodAtInput(contract, blockNum);
        TransactionResult res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));

        blockNum = numBlocks;
        timestamp += 4;
        mockBlockchainBlock(blockNum, timestamp);
        input = getPeriodAtInput(contract, blockNum);
        res = newTRSqueryContract(AION).execute(input, COST);
        assertEquals(ResultCode.SUCCESS, res.getResultCode());
        assertEquals(0, res.getEnergyRemaining());
        assertEquals(BigInteger.ONE, new BigInteger(res.getOutput()));
    }

    // <-------------------------AVAILABLE FOR WITHDRAWAL AT TRS TESTS----------------------------->

    @Test
    public void testAvailableForInputTooShort() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7, BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        byte[] shortInput = Arrays.copyOf(input, input.length - 1);
        assertEquals(
                ResultCode.FAILURE,
                newTRSqueryContract(acct).execute(shortInput, COST).getResultCode());
    }

    @Test
    public void testAvailableForInputTooLong() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7, BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        byte[] longInput = new byte[input.length + 1];
        System.arraycopy(input, 0, longInput, 0, input.length);
        assertEquals(
                ResultCode.FAILURE,
                newTRSqueryContract(acct).execute(longInput, COST).getResultCode());
    }

    @Test
    public void testAvailableForContractNonExistent() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        byte[] input = getAvailableForWithdrawalAtInput(acct, 0);
        assertEquals(
                ResultCode.FAILURE,
                newTRSqueryContract(acct).execute(input, COST).getResultCode());
    }

    @Test
    public void testAvailableForContractNotYetLive() {
        Address acct = getNewExistentAccount(DEFAULT_BALANCE);
        Address contract = createTRScontract(acct, false, true, 7, BigInteger.ZERO, 0);
        byte[] input = getAvailableForWithdrawalAtInput(contract, 0);
        assertEquals(
                ResultCode.FAILURE,
                newTRSqueryContract(acct).execute(input, COST).getResultCode());
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
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 12;
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
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
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 6;
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
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
            TransactionResult res = newTRSqueryContract(acc).execute(input, COST);
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
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
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
            TransactionResult res = newTRSqueryContract(acc).execute(input, COST);
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
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + (periods / 3);
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);

        timestamp = trs.getTimestamp(contract) + periods - 1;
        checkAvailableForResults(
                trs, contract, timestamp, numDepositors, deposits, bonus, percent, periods);
    }
}
