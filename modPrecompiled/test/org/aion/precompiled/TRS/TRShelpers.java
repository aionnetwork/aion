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

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.type.AionAddress;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.contracts.TRS.TRSstateContract;
import org.aion.precompiled.contracts.TRS.TRSuseContract;
import org.aion.base.vm.IDataWord;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Assert;

/** A class exposing all helper methods and some variables for TRS-related testing. */
class TRShelpers {
    private static final byte[] OUT = new byte[1];
    static final BigInteger DEFAULT_BALANCE = BigInteger.TEN;
    private IAionBlockchain blockchain = StandaloneBlockchain.inst();
    AionAddress AION =
            AionAddress.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    List<AionAddress> tempAddrs;
    ECKey senderKey;
    long COST = 21000L;

    // Returns a new account with initial balance balance that exists in the repo.
    AionAddress getNewExistentAccount(BigInteger balance) {
        AionAddress acct = AionAddress.wrap(ECKeyFac.inst().create().getAddress());
        acct.toBytes()[0] = (byte) 0xA0;
        repo.createAccount(acct);
        repo.addBalance(acct, balance);
        repo.flush();
        tempAddrs.add(acct);
        return acct;
    }

    // Returns a new TRSstateContract that calls the contract using caller.
    TRSstateContract newTRSstateContract(AionAddress caller) {
        return new TRSstateContract(repo, caller, blockchain);
    }

    // Returns a new TRSuseContract that calls the contract using caller.
    TRSuseContract newTRSuseContract(AionAddress caller) {
        return new TRSuseContract(repo, caller, blockchain);
    }

    // Returns a new TRSqueryContract that calls the contract using caller.
    TRSqueryContract newTRSqueryContract(AionAddress caller) {
        return new TRSqueryContract(repo, caller, blockchain);
    }

    // Returns the address of a newly created TRS contract, assumes all params are valid.
    AionAddress createTRScontract(
            AionAddress owner,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        byte[] input = getCreateInput(isTest, isDirectDeposit, periods, percent, precision);
        TRSstateContract trs = new TRSstateContract(repo, owner, blockchain);
        TransactionResult res = trs.execute(input, COST);
        if (!res.getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Unable to create contract!");
        }
        AionAddress contract = new AionAddress(res.getOutput());
        tempAddrs.add(contract);
        repo.incrementNonce(owner);
        repo.flush();
        return contract;
    }

    // Returns the address of a newly created TRS contract and locks it; assumes all params valid.
    // The owner deposits 1 token so that the contract can be locked.
    AionAddress createAndLockTRScontract(
            AionAddress owner,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        AionAddress contract =
                createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        if (!newTRSuseContract(owner)
                .execute(input, COST)
                .getResultCode()
                .equals(ResultCode.SUCCESS)) {
            fail(
                    "Owner failed to deposit 1 token into contract! Owner balance is: "
                            + repo.getBalance(owner));
        }
        input = getLockInput(contract);
        if (!newTRSstateContract(owner)
                .execute(input, COST)
                .getResultCode()
                .equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        return contract;
    }

    // Returns the address of a newly created TRS contract that is locked and live. The owner
    // deposits
    //  token so that the contract can be locked.
    AionAddress createLockedAndLiveTRScontract(
            AionAddress owner,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        AionAddress contract =
                createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        if (!newTRSuseContract(owner)
                .execute(input, COST)
                .getResultCode()
                .equals(ResultCode.SUCCESS)) {
            fail(
                    "Owner failed to deposit 1 token into contract! Owner balance is: "
                            + repo.getBalance(owner));
        }
        input = getLockInput(contract);
        if (!newTRSstateContract(owner)
                .execute(input, COST)
                .getResultCode()
                .equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        input = getStartInput(contract);
        if (!newTRSstateContract(owner)
                .execute(input, COST)
                .getResultCode()
                .equals(ResultCode.SUCCESS)) {
            fail("Failed to start contract!");
        }
        return contract;
    }

    // Locks and makes contract live, where owner is owner of the contract.
    void lockAndStartContract(AionAddress contract, AionAddress owner) {
        byte[] input = getLockInput(contract);
        AbstractTRS trs = newTRSstateContract(owner);
        if (!trs.execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            Assert.fail("Unable to lock contract!");
        }
        input = getStartInput(contract);
        if (!trs.execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            Assert.fail("Unable to start contract!");
        }
    }

    // Returns a TRS contract address that has numDepositors depositors in it (owner does not
    // deposit)
    // each with DEFAULT_BALANCE deposit balance.
    AionAddress getContractMultipleDepositors(
            int numDepositors,
            AionAddress owner,
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

        AionAddress contract =
                createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        for (int i = 0; i < numDepositors; i++) {
            AionAddress acct = getNewExistentAccount(DEFAULT_BALANCE);
            if (!newTRSuseContract(acct)
                    .execute(input, COST)
                    .getResultCode()
                    .equals(ResultCode.SUCCESS)) {
                Assert.fail("Depositor #" + i + " failed to deposit!");
            }
        }
        return contract;
    }

    // Returns a TRS contract address that has numDepositors depositors in it (owner does not
    // deposit)
    // each with DEFAULT_BALANCE deposit balance. Owner uses depositFor to deposit for depositors.
    AionAddress getContractMultipleDepositorsUsingDepositFor(
            int numDepositors,
            AionAddress owner,
            boolean isTest,
            int periods,
            BigInteger percent,
            int precision) {

        AionAddress contract = createTRScontract(owner, isTest, false, periods, percent, precision);
        AbstractTRS trs = newTRSuseContract(owner);
        for (int i = 0; i < numDepositors; i++) {
            AionAddress acct = getNewExistentAccount(BigInteger.ZERO);
            byte[] input = getDepositForInput(contract, acct, DEFAULT_BALANCE);
            if (!trs.execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                Assert.fail("Depositor #" + i + " failed to deposit!");
            }
        }
        return contract;
    }

    /**
     * Sets the blockchain to a mocked blockchain whose best block has the timestamp timestamp.
     *
     * @param timestamp The timestamp of the mocked best block.
     */
    void mockBlockchain(long timestamp) {
        AionBlock mockblock = mock(AionBlock.class);
        when(mockblock.getTimestamp()).thenReturn(timestamp);
        StandaloneBlockchain mockchain = mock(StandaloneBlockchain.class);
        when(mockchain.getBestBlock()).thenReturn(mockblock);
        blockchain = mockchain;
    }

    /**
     * Sets the block at block number blockNum to have timestamp timestamp.
     *
     * @param blockNum The block number to mock.
     * @param timestamp The mocked timestamp.
     */
    void mockBlockchainBlock(long blockNum, long timestamp) {
        AionBlock mockBlock = mock(AionBlock.class);
        when(mockBlock.getTimestamp()).thenReturn(timestamp);
        when(blockchain.getBlockByNumber(blockNum)).thenReturn(mockBlock);
    }

    // Returns the amount of extra funds that the TRS contract contract has.
    BigInteger getExtraFunds(AbstractTRS trs, AionAddress contract) {
        return trs.getExtraFunds(contract);
    }

    // Returns the period the contract is in at the block number blockNum.
    BigInteger getPeriodAt(AbstractTRS trs, AionAddress contract, long blockNum) {
        long timestamp = blockchain.getBlockByNumber(blockNum).getTimestamp();
        return BigInteger.valueOf(
                trs.calculatePeriod(contract, getContractSpecs(trs, contract), timestamp));
    }

    // Returns the percentage configured for the TRS contract.
    BigDecimal getPercentage(AbstractTRS trs, AionAddress contract) {
        return AbstractTRS.getPercentage(trs.getContractSpecs(contract));
    }

    // Returns the periods configured for the TRS contract.
    int getPeriods(AbstractTRS trs, AionAddress contract) {
        return AbstractTRS.getPeriods(trs.getContractSpecs(contract));
    }

    // Returns the deposit balance of account in the TRS contract contract.
    BigInteger getDepositBalance(AbstractTRS trs, AionAddress contract, AionAddress account) {
        return trs.getDepositBalance(contract, account);
    }

    // Returns the balance of bonus tokens in the TRS contract contract.
    BigInteger getBonusBalance(AbstractTRS trs, AionAddress contract) {
        return trs.getBonusBalance(contract);
    }

    // Returns true only if the TRS contract has its funds open.
    boolean getAreContractFundsOpen(AbstractTRS trs, AionAddress contract) {
        return trs.isOpenFunds(contract);
    }

    // Returns the total deposit balance for the TRS contract contract.
    BigInteger getTotalBalance(AbstractTRS trs, AionAddress contract) {
        return trs.getTotalBalance(contract);
    }

    // Returns true only if account is a valid account in contract.
    boolean accountIsValid(AbstractTRS trs, AionAddress contract, AionAddress account) {
        try {
            return AbstractTRS.accountIsValid(trs.getListNextBytes(contract, account));
        } catch (Exception e) {
            // Since we possibly call on a non-existent account.
            return false;
        }
    }

    // Returns the address of the owner of the TRS contract given by the address contract.
    AionAddress getOwner(AbstractTRS trs, AionAddress contract) {
        return trs.getContractOwner(contract);
    }

    // Returns true only if the TRS contract is in Test Mode.
    boolean isTestContract(AbstractTRS trs, AionAddress contract) {
        return AbstractTRS.isTestContract(trs.getContractSpecs(contract));
    }

    // Returns true only if the TRS contract has direct deposit enabled.
    boolean isDirectDepositEnabled(AbstractTRS trs, AionAddress contract) {
        return trs.isDirDepositsEnabled(contract);
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is locked.
    boolean isContractLocked(AbstractTRS trs, AionAddress contract) {
        return trs.isContractLocked(contract);
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is live.
    boolean isContractLive(AbstractTRS trs, AionAddress contract) {
        return trs.isContractLive(contract);
    }

    // Returns the timestamp for this contract.
    long getContractTimestamp(AbstractTRS trs, AionAddress contract) {
        return trs.getTimestamp(contract);
    }

    // Returns true only if account is eligible to use the special one-off withdrawal event.
    boolean accountIsEligibleForSpecial(TRSuseContract trs, AionAddress contract, AionAddress account) {
        return trs.accountIsEligibleForSpecial(contract, account);
    }

    // Returns the last period in which account made a withdrawal or -1 if bad contract or account.
    int getAccountLastWithdrawalPeriod(AbstractTRS trs, AionAddress contract, AionAddress account) {
        return trs.getAccountLastWithdrawalPeriod(contract, account);
    }

    // Returns the share of the bonus tokens account is entitled to withdraw over life of contract.
    BigInteger getBonusShare(AbstractTRS trs, AionAddress contract, AionAddress account) {
        return trs.computeBonusShare(contract, account);
    }

    // Returns the amount of extras account is able to withdraw in the current period.
    BigInteger getExtraShare(
            AbstractTRS trs,
            AionAddress contract,
            AionAddress account,
            BigDecimal fraction,
            int currPeriod) {
        return trs.computeExtraFundsToWithdraw(contract, account, fraction, currPeriod);
    }

    // Grabs the current period contract is in as a BigInteger.
    BigInteger getCurrentPeriod(AbstractTRS trs, AionAddress contract) {
        return BigInteger.valueOf(getContractCurrentPeriod(trs, contract));
    }

    // Grabs the amount an account can withdraw in special event according to these params.
    BigInteger getSpecialAmount(
            BigDecimal accBalance,
            BigDecimal contractBalance,
            BigDecimal bonus,
            BigDecimal percent) {

        BigDecimal owings = new BigDecimal(grabOwings(accBalance, contractBalance, bonus));
        return (owings.multiply(percent.movePointLeft(2))).toBigInteger();
    }

    // Grabs the amount an account can withdraw regularly, not in special event, according to
    // params.
    BigInteger getWithdrawAmt(BigInteger owings, BigInteger specialAmt, int periods) {
        BigDecimal periodsBI = new BigDecimal(BigInteger.valueOf(periods));
        BigDecimal owingWithoutSpec = new BigDecimal(owings.subtract(specialAmt));
        BigDecimal res = owingWithoutSpec.divide(periodsBI, 18, RoundingMode.HALF_DOWN);
        return res.toBigInteger();
    }

    // Returns the period that contract is currently in.
    int getContractCurrentPeriod(AbstractTRS trs, AionAddress contract) {
        long currTime = blockchain.getBestBlock().getTimestamp();
        return trs.calculatePeriod(contract, trs.getContractSpecs(contract), currTime);
    }

    // Grabs the amount owed to account if the following params are true.
    BigInteger grabOwings(BigDecimal accBalance, BigDecimal contractBalance, BigDecimal bonus) {
        BigDecimal fraction = accBalance.divide(contractBalance, 18, RoundingMode.HALF_DOWN);
        BigDecimal share = bonus.multiply(fraction);
        return share.toBigInteger().add(accBalance.toBigInteger());
    }

    // Returns the total amount of tokens account can withdraw over the lifetime of the contract.
    BigInteger getTotalOwed(AbstractTRS trs, AionAddress contract, AionAddress account) {
        return trs.computeTotalOwed(contract, account);
    }

    /**
     * Returns the amount an account is expected to receive from a first withdrawal from a contract
     * given that the params are true of the contract and the caller.
     *
     * <p>This method is unreliable if the first withdraw is performed in the last period. There are
     * roundoff errors that accrue and cause the non-final withdrawal amounts to be round down and
     * their sum may be less than the total owed. The contract handles the final period specially to
     * ensure all funds owed are paid OUT.
     *
     * @param trs An AbstractTRS instance.
     * @param contract The contract in question.
     * @param deposits The amount the caller deposited.
     * @param total The total amount of deposits in the contract.
     * @param bonus The bonus balance in the contract.
     * @param percent The percentage of total owings the caller is eligible to receive in special
     *     event.
     * @param periods The number of periods the contract has.
     * @return the expected amount to withdraw on a first call to the contract.
     */
    BigInteger expectedAmtFirstWithdraw(
            AbstractTRS trs,
            AionAddress contract,
            BigInteger deposits,
            BigInteger total,
            BigInteger bonus,
            BigDecimal percent,
            int periods) {

        BigInteger currPeriod = getCurrentPeriod(trs, contract);
        BigInteger owings =
                grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger expectedSpecial =
                getSpecialAmount(
                        new BigDecimal(deposits),
                        new BigDecimal(total),
                        new BigDecimal(bonus),
                        percent);
        BigInteger expectedWithdraw =
                currPeriod.multiply(getWithdrawAmt(owings, expectedSpecial, periods));
        return expectedWithdraw.add(expectedSpecial);
    }

    /**
     * Creates a contract with AION as the owner and has numDepositors deposit deposits amount each.
     * A bonus deposit of bonus is made. Then the contract is locked and made live.
     *
     * <p>The contract is set to be in testing mode. It has a total of periods periods and
     * percentage is the percent of the total owings that an account can withdraw in the special
     * one-off event.
     *
     * <p>The owner does not deposit.
     *
     * @param numDepositors The number of depositors, excluding owner, who deposit into contract.
     * @param deposits The amount each depositor deposits.
     * @param bonus The bonus amount.
     * @param periods The number of periods the contract has.
     * @param percentage The percent of total owings that can be claimed in special event.
     * @return the address of the contract.
     */
    AionAddress setupContract(
            int numDepositors,
            BigInteger deposits,
            BigInteger bonus,
            int periods,
            BigDecimal percentage) {

        int precision = percentage.scale();
        BigInteger percent = percentage.movePointRight(precision).toBigInteger();
        AionAddress contract = createTRScontract(AION, true, true, periods, percent, precision);

        assertEquals(percentage, getPercentage(newTRSstateContract(AION), contract));
        assertEquals(periods, getPeriods(newTRSstateContract(AION), contract));

        byte[] input = getDepositInput(contract, deposits);
        for (int i = 0; i < numDepositors; i++) {
            AionAddress acc = getNewExistentAccount(deposits);
            assertEquals(
                    ResultCode.SUCCESS,
                    newTRSuseContract(acc).execute(input, COST).getResultCode());
        }
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, AION);
        return contract;
    }

    // Returns an input byte array for the create operation using the provided parameters.
    byte[] getCreateInput(
            boolean isTest,
            boolean isDirectDeposit,
            int periods,
            BigInteger percent,
            int precision) {

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
            System.arraycopy(
                    percentBytes, 0, input, 14 - percentBytes.length - 1, percentBytes.length);
        }
        input[13] = (byte) precision;
        return input;
    }

    // Returns an input byte array for the lock operation using the provided parameters.
    byte[] getLockInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns an input byte array for the start operation using the provided parameters.
    byte[] getStartInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation.
    byte[] getDepositInput(AionAddress contract, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) {
            fail();
        }
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit-for operation.
    byte[] getDepositForInput(AionAddress contract, AionAddress beneficiary, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) {
            fail();
        }
        byte[] input = new byte[193];
        input[0] = 0x5;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        System.arraycopy(beneficiary.toBytes(), 0, input, 33, AionAddress.SIZE);
        System.arraycopy(amtBytes, 0, input, 193 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the withdraw operation.
    byte[] getWithdrawInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the refund operation.
    byte[] getRefundInput(AionAddress contract, AionAddress account, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) {
            Assert.fail();
        }
        byte[] input = new byte[193];
        input[0] = 0x4;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        System.arraycopy(account.toBytes(), 0, input, 33, AionAddress.SIZE);
        System.arraycopy(amtBytes, 0, input, 193 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isLive (or isStarted)
    // operation.
    byte[] getIsLiveInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isLocked operation.
    byte[] getIsLockedInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isDirectDepositEnabled
    // op.
    byte[] getIsDirDepoEnabledInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the period operation.
    byte[] getPeriodInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the periodsAt operation.
    byte[] getPeriodAtInput(AionAddress contract, long blockNum) {
        ByteBuffer buffer = ByteBuffer.allocate(41);
        buffer.put((byte) 0x4);
        buffer.put(contract.toBytes());
        buffer.putLong(blockNum);
        return Arrays.copyOf(buffer.array(), 41);
    }

    // Returns a properly formatted byte array to be used as input for the bulk-withdraw operation.
    byte[] getBulkWithdrawInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the open-funds operation.
    byte[] getOpenFundsInput(AionAddress contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the available-for-..
    // operation.
    byte[] getAvailableForWithdrawalAtInput(AionAddress contract, long timestamp) {
        byte[] input = new byte[41];
        input[0] = 0x5;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(timestamp);
        byte[] buf = buffer.array();
        System.arraycopy(buf, 0, input, input.length - buf.length, buf.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the addExtraFunds operation.
    byte[] getAddExtraInput(AionAddress contract, BigInteger amount) {
        byte[] input = new byte[161];
        input[0] = 0x6;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) {
            fail();
        }
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to add the max extra funds in one operation.
    byte[] getAddExtraMaxInput(AionAddress contract) {
        byte[] input = new byte[161];
        input[0] = 0x6;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        for (int i = 33; i < 161; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a properly formatted byte array to bulk deposit for each account in beneficiaries.
    // This method does: deposit amounts[i] on behalf of beneficiaries[i]
    byte[] getBulkDepositForInput(AionAddress contract, AionAddress[] beneficiaries, BigInteger[] amounts) {
        int len = beneficiaries.length;
        if ((len < 1) || (len > 100)) {
            fail("Imporper length: " + len);
        }
        if (len != amounts.length) {
            fail("beneficiaries and amounts differ in length!");
        }
        int arrLen = 33 + (len * 32) + (len * 128);

        byte[] input = new byte[arrLen];
        input[0] = 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        int index = 33;
        for (int i = 0; i < len; i++) {
            System.arraycopy(beneficiaries[i].toBytes(), 0, input, index, AionAddress.SIZE);
            index += 32 + 128;
            byte[] amtBytes = amounts[i].toByteArray();
            if (amtBytes.length > 128) {
                fail();
            }
            System.arraycopy(amtBytes, 0, input, index - amtBytes.length, amtBytes.length);
        }
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the refund operation, to
    // refund the maximum allowable amount.
    byte[] getMaxRefundInput(AionAddress contract, AionAddress account) {
        byte[] input = new byte[193];
        input[0] = 0x4;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        System.arraycopy(account.toBytes(), 0, input, 33, AionAddress.SIZE);
        for (int i = 65; i < 193; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation, to
    // deposit the maximum allowable amount.
    byte[] getMaxDepositInput(AionAddress contract) {
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, AionAddress.SIZE);
        for (int i = 33; i < 161; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Makes input for numBeneficiaries beneficiaries who each receive a deposit amount deposits.
    byte[] makeBulkDepositForInput(AionAddress contract, int numBeneficiaries, BigInteger deposits) {
        AionAddress[] beneficiaries = new AionAddress[numBeneficiaries];
        BigInteger[] amounts = new BigInteger[numBeneficiaries];
        for (int i = 0; i < numBeneficiaries; i++) {
            beneficiaries[i] = getNewExistentAccount(BigInteger.ZERO);
            amounts[i] = deposits;
        }
        return getBulkDepositForInput(contract, beneficiaries, amounts);
    }

    // Makes input for numOthers beneficiaries and self who each receive a deposit amount deposits.
    byte[] makeBulkDepositForInputwithSelf(
            AionAddress contract, AionAddress self, int numOthers, BigInteger deposits) {

        AionAddress[] beneficiaries = new AionAddress[numOthers + 1];
        BigInteger[] amounts = new BigInteger[numOthers + 1];
        for (int i = 0; i < numOthers; i++) {
            beneficiaries[i] = getNewExistentAccount(BigInteger.ZERO);
            amounts[i] = deposits;
        }
        beneficiaries[numOthers] = self;
        amounts[numOthers] = deposits;
        return getBulkDepositForInput(contract, beneficiaries, amounts);
    }

    /**
     * Returns a set of all the accounts that have deposited a positive amount of tokens into the
     * TRS contract contract.
     *
     * @param contract The TRS contract to query.
     * @return the set of all depositors.
     */
    Set<AionAddress> getAllDepositors(AbstractTRS trs, AionAddress contract) {
        Set<AionAddress> depositors = new HashSet<>();
        AionAddress curr = getLinkedListHead(trs, contract);
        while (curr != null) {
            assertFalse(depositors.contains(curr));
            depositors.add(curr);
            curr = getLinkedListNext(trs, contract, curr);
        }
        return depositors;
    }

    // Returns the head of the list for contract or null if no head.
    AionAddress getLinkedListHead(AbstractTRS trs, AionAddress contract) {
        byte[] head = trs.getListHead(contract);
        if (head == null) {
            return null;
        }
        head[0] = (byte) 0xA0;
        return new AionAddress(head);
    }

    // Returns the next account in the linked list after current, or null if no next.
    AionAddress getLinkedListNext(AbstractTRS trs, AionAddress contract, AionAddress current) {
        byte[] next = trs.getListNextBytes(contract, current);
        boolean noNext = (((next[0] & 0x80) == 0x80) || ((next[0] & 0x40) == 0x00));
        if (noNext) {
            return null;
        }
        next[0] = (byte) 0xA0;
        return new AionAddress(next);
    }

    // Returns the previous account in the linked list prior to current, or null if no previous.
    AionAddress getLinkedListPrev(AbstractTRS trs, AionAddress contract, AionAddress current) {
        byte[] prev = trs.getListPrev(contract, current);
        if (prev == null) {
            return null;
        }
        prev[0] = (byte) 0xA0;
        return new AionAddress(prev);
    }

    // Checks that each account is paid out correctly when they withdraw in a non-final period.
    void checkPayoutsNonFinal(
            AbstractTRS trs,
            AionAddress contract,
            int numDepositors,
            BigInteger deposits,
            BigInteger bonus,
            BigDecimal percent,
            int periods,
            int currPeriod) {

        Set<AionAddress> contributors = getAllDepositors(trs, contract);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigDecimal fraction =
                BigDecimal.ONE.divide(new BigDecimal(numDepositors), 18, RoundingMode.HALF_DOWN);

        for (AionAddress acc : contributors) {
            BigInteger extraShare = getExtraShare(trs, contract, acc, fraction, currPeriod);
            BigInteger amt =
                    expectedAmtFirstWithdraw(
                            trs, contract, deposits, total, bonus, percent, periods);

            byte[] input = getWithdrawInput(contract);
            assertEquals(
                    ResultCode.SUCCESS,
                    newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt.add(extraShare), repo.getBalance(acc));
        }
    }

    // Checks that each account is paid out correctly when they withdraw in a final period.
    void checkPayoutsFinal(
            AbstractTRS trs,
            AionAddress contract,
            int numDepositors,
            BigInteger deposits,
            BigInteger bonus,
            BigInteger extra) {

        Set<AionAddress> contributors = getAllDepositors(trs, contract);
        BigDecimal fraction =
                BigDecimal.ONE.divide(new BigDecimal(numDepositors), 18, RoundingMode.HALF_DOWN);
        BigInteger extraShare = fraction.multiply(new BigDecimal(extra)).toBigInteger();
        BigInteger amt =
                (fraction.multiply(new BigDecimal(bonus)))
                        .add(new BigDecimal(deposits))
                        .toBigInteger();
        BigInteger collected = amt.add(extraShare);

        for (AionAddress acc : contributors) {
            byte[] input = getWithdrawInput(contract);
            assertEquals(
                    ResultCode.SUCCESS,
                    newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(collected, repo.getBalance(acc));
        }

        // Verify that the contract has enough funds to pay OUT.
        BigInteger contractTotal =
                deposits.multiply(BigInteger.valueOf(numDepositors)).add(bonus).add(extra);
        assertTrue(
                collected.multiply(BigInteger.valueOf(numDepositors)).compareTo(contractTotal)
                        <= 0);
    }

    // Checks that availableForWithdrawalAt returns expected results for the specified query.
    void checkAvailableForResults(
            AbstractTRS trs,
            AionAddress contract,
            long timestamp,
            int numDepositors,
            BigInteger deposits,
            BigInteger bonus,
            BigDecimal percent,
            int periods) {
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigInteger owings =
                grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger amt =
                expectedAmtFirstWithdraw(
                        trs, contract, deposits, total, bonus, percent, periods, timestamp);

        BigDecimal expectedFraction =
                new BigDecimal(amt).divide(new BigDecimal(owings), 18, RoundingMode.HALF_DOWN);

        byte[] input = getAvailableForWithdrawalAtInput(contract, timestamp);
        Set<AionAddress> contributors = getAllDepositors(trs, contract);
        for (AionAddress acc : contributors) {
            TransactionResult res = newTRSqueryContract(acc).execute(input, COST);
            assertEquals(ResultCode.SUCCESS, res.getResultCode());
            BigDecimal frac = new BigDecimal(new BigInteger(res.getOutput())).movePointLeft(18);
            assertEquals(expectedFraction, frac);
        }
    }

    // Returns the maximum amount that can be deposited in a single deposit call.
    BigInteger getMaxOneTimeDeposit() {
        return BigInteger.TWO.pow(1024).subtract(BigInteger.ONE);
    }

    // Returns the maximum amount that a single account can deposit into a TRS contract.
    BigInteger getMaxTotalDeposit() {
        return BigInteger.TWO.pow(4096).subtract(BigInteger.ONE);
    }

    // Returns a byte array signalling false for a TRS contract query operation result.
    byte[] getFalseContractOutput() {
        OUT[0] = 0x0;
        return OUT;
    }

    // Returns a byte array signalling true for a TRS contract query operation result.
    byte[] getTrueContractOutput() {
        OUT[0] = 0x1;
        return OUT;
    }

    // Returns a new DataWordStub that wraps data. Here so we can switch types easy if needed.
    IDataWord newDataWordStub(byte[] data) {
        if (data.length == DataWord.BYTES) {
            return new DataWord(data);
        } else if (data.length == DoubleDataWord.BYTES) {
            return new DoubleDataWord(data);
        } else {
            fail();
        }
        return null;
    }

    // <-----------------------------------HELPERS FOR THIS CLASS---------------------------------->

    /**
     * Returns the amount an account is expected to receive from a first withdrawal from a contract
     * given that the params are true of the contract and the caller.
     *
     * <p>This method uses timestamp to determine the current period the contract is in!
     *
     * <p>This method is unreliable if the first withdraw is performed in the last period. There are
     * roundoff errors that accrue and cause the non-final withdrawal amounts to be round down and
     * their sum may be less than the total owed. The contract handles the final period specially to
     * ensure all funds owed are paid OUT.
     *
     * @param trs An AbstractTRS instance.
     * @param contract The contract in question.
     * @param deposits The amount the caller deposited.
     * @param total The total amount of deposits in the contract.
     * @param bonus The bonus balance in the contract.
     * @param percent The percentage of total owings the caller is eligible to receive in special
     *     event.
     * @param periods The number of periods the contract has.
     * @param timestamp Timestamp for the current period the contract is in.
     * @return the expected amount to withdraw on a first call to the contract.
     */
    private BigInteger expectedAmtFirstWithdraw(
            AbstractTRS trs,
            AionAddress contract,
            BigInteger deposits,
            BigInteger total,
            BigInteger bonus,
            BigDecimal percent,
            int periods,
            long timestamp) {

        // TODO - this should be computed here, not grabbed from the contract
        BigInteger currPeriod =
                BigInteger.valueOf(
                        trs.calculatePeriod(contract, getContractSpecs(trs, contract), timestamp));
        BigInteger owings =
                grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger expectedSpecial =
                getSpecialAmount(
                        new BigDecimal(deposits),
                        new BigDecimal(total),
                        new BigDecimal(bonus),
                        percent);
        BigInteger expectedWithdraw =
                currPeriod.multiply(getWithdrawAmt(owings, expectedSpecial, periods));
        return expectedWithdraw.add(expectedSpecial);
    }

    // Returns the contract specifications byte array associated with contract.
    private byte[] getContractSpecs(AbstractTRS trs, AionAddress contract) {
        return trs.getContractSpecs(contract);
    }
}
