package org.aion.precompiled.TRS;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSstateContract;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.contracts.TRS.TRSuseContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Assert;

/**
 * A class exposing all helper methods and some variables for TRS-related testing.
 */
class TRShelpers {
    private static final byte[] OUT = new byte[1];
    static final BigInteger DEFAULT_BALANCE = BigInteger.TEN;
    private IAionBlockchain blockchain = StandaloneBlockchain.inst();
    Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    List<Address> tempAddrs;
    ECKey senderKey;
    long COST = 21000L;

    // Returns a new account with initial balance balance that exists in the repo.
    Address getNewExistentAccount(BigInteger balance) {
        Address acct = Address.wrap(ECKeyFac.inst().create().getAddress());
        acct.toBytes()[0] = (byte) 0xA0;
        repo.createAccount(acct);
        repo.addBalance(acct, balance);
        repo.flush();
        tempAddrs.add(acct);
        return acct;
    }

    // Returns a new TRSstateContract that calls the contract using caller.
    TRSstateContract newTRSstateContract(Address caller) {
        return new TRSstateContract(repo, caller, blockchain);
    }

    // Returns a new TRSuseContract that calls the contract using caller.
    TRSuseContract newTRSuseContract(Address caller) {
        return new TRSuseContract(repo, caller, blockchain);
    }

    // Returns a new TRSqueryContract that calls the contract using caller.
    TRSqueryContract newTRSqueryContract(Address caller) { return new TRSqueryContract(repo, caller, blockchain); }

    // Returns the address of a newly created TRS contract, assumes all params are valid.
    Address createTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        byte[] input = getCreateInput(isTest, isDirectDeposit, periods, percent, precision);
        TRSstateContract trs = new TRSstateContract(repo, owner, blockchain);
        ExecutionResult res = trs.execute(input, COST);
        if (!res.getResultCode().equals(ResultCode.SUCCESS)) { fail("Unable to create contract!"); }
        Address contract = new Address(res.getOutput());
        tempAddrs.add(contract);
        repo.incrementNonce(owner);
        repo.flush();
        return contract;
    }

    // Returns the address of a newly created TRS contract and locks it; assumes all params valid.
    // The owner deposits 1 token so that the contract can be locked.
    Address createAndLockTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        if (!newTRSuseContract(owner).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Owner failed to deposit 1 token into contract! Owner balance is: " + repo.getBalance(owner));
        }
        input = getLockInput(contract);
        if (!newTRSstateContract(owner).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        return contract;
    }

    // Returns the address of a newly created TRS contract that is locked and live. The owner deposits
    //  token so that the contract can be locked.
    Address createLockedAndLiveTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, BigInteger.ONE);
        if (!newTRSuseContract(owner).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Owner failed to deposit 1 token into contract! Owner balance is: " + repo.getBalance(owner));
        }
        input = getLockInput(contract);
        if (!newTRSstateContract(owner).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        input = getStartInput(contract);
        if (!newTRSstateContract(owner).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to start contract!");
        }
        return contract;
    }

    // Locks and makes contract live, where owner is owner of the contract.
    void lockAndStartContract(Address contract, Address owner) {
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

    // Returns a TRS contract address that has numDepositors depositors in it (owner does not deposit)
    // each with DEFAULT_BALANCE deposit balance.
    Address getContractMultipleDepositors(int numDepositors, Address owner, boolean isTest,
        boolean isDirectDeposit, int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getDepositInput(contract, DEFAULT_BALANCE);
        for (int i = 0; i < numDepositors; i++) {
            Address acct = getNewExistentAccount(DEFAULT_BALANCE);
            if (!newTRSuseContract(acct).execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                Assert.fail("Depositor #" + i + " failed to deposit!");
            }
        }
        return contract;
    }

    // Returns a TRS contract address that has numDepositors depositors in it (owner does not deposit)
    // each with DEFAULT_BALANCE deposit balance. Owner uses depositFor to deposit for depositors.
    Address getContractMultipleDepositorsUsingDepositFor(int numDepositors, Address owner,
        boolean isTest, int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, false, periods, percent, precision);
        AbstractTRS trs = newTRSuseContract(owner);
        for (int i = 0; i < numDepositors; i++) {
            Address acct = getNewExistentAccount(BigInteger.ZERO);
            byte[] input = getDepositForInput(contract, acct, DEFAULT_BALANCE);
            if (!trs.execute(input, COST).getResultCode().equals(ResultCode.SUCCESS)) {
                Assert.fail("Depositor #" + i + " failed to deposit!");
            }
        }
        return contract;
    }

    // Creates a new blockchain with numBlocks blocks and sets it to the blockchain field. This
    // method creates a new block every sleepDuration milliseconds.
    void createBlockchain(int numBlocks, long sleepDuration) throws InterruptedException {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .build();

        StandaloneBlockchain bc = bundle.bc;
        senderKey = bundle.privateKeys.get(0);
        AionBlock previousBlock = bc.genesis;

        for (int i = 0; i < numBlocks; i++) {
            if (sleepDuration > 0) { Thread.sleep(sleepDuration); }
            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
        }

        blockchain = bc;
    }

    // Adds numBlocks more blocks to the blockchain every sleepDuration milliseconds.
    void addBlocks(int numBlocks, long sleepDuration) throws InterruptedException {
        AionBlock previousBlock = blockchain.getBestBlock();
        for (int i = 0; i < numBlocks; i++) {
            if (sleepDuration > 0) { Thread.sleep(sleepDuration); }
            previousBlock = createBundleAndCheck(((StandaloneBlockchain) blockchain), senderKey, previousBlock);
        }
    }

    // Returns the amount of extra funds that the TRS contract contract has.
    BigInteger getExtraFunds(AbstractTRS trs, Address contract) {
        return trs.getExtraFunds(contract);
    }

    // Returns the period the contract is in at the block number blockNum.
    BigInteger getPeriodAt(AbstractTRS trs, Address contract, long blockNum) {
        long timestamp = blockchain.getBlockByNumber(blockNum).getTimestamp();
        return BigInteger.valueOf(trs.calculatePeriod(contract, getContractSpecs(trs, contract), timestamp));
    }

    // Returns the percentage configured for the TRS contract.
    BigDecimal getPercentage(AbstractTRS trs, Address contract) {
        return AbstractTRS.getPercentage(trs.getContractSpecs(contract));
    }

    // Returns the periods configured for the TRS contract.
    int getPeriods(AbstractTRS trs, Address contract) {
        return AbstractTRS.getPeriods(trs.getContractSpecs(contract));
    }

    // Returns the deposit balance of account in the TRS contract contract.
    BigInteger getDepositBalance(AbstractTRS trs, Address contract, Address account) {
        return trs.getDepositBalance(contract, account);
    }

    // Returns the balance of bonus tokens in the TRS contract contract.
    BigInteger getBonusBalance(AbstractTRS trs, Address contract) {
        return trs.getBonusBalance(contract);
    }

    // Returns true only if the TRS contract has its funds open.
    boolean getAreContractFundsOpen(AbstractTRS trs, Address contract) {
        return trs.isOpenFunds(contract);
    }

    // Returns the total deposit balance for the TRS contract contract.
    BigInteger getTotalBalance(AbstractTRS trs, Address contract) {
        return trs.getTotalBalance(contract);
    }

    // Returns true only if account is a valid account in contract.
    boolean accountIsValid(AbstractTRS trs, Address contract, Address account) {
        try {
            return AbstractTRS.accountIsValid(trs.getListNextBytes(contract, account));
        } catch (Exception e) {
            // Since we possibly call on a non-existent account.
            return false;
        }
    }

    // Returns the address of the owner of the TRS contract given by the address contract.
    Address getOwner(AbstractTRS trs, Address contract) {
        return trs.getContractOwner(contract);
    }

    // Returns true only if the TRS contract is in Test Mode.
    boolean isTestContract(AbstractTRS trs, Address contract) {
        return AbstractTRS.isTestContract(trs.getContractSpecs(contract));
    }

    // Returns true only if the TRS contract has direct deposit enabled.
    boolean isDirectDepositEnabled(AbstractTRS trs, Address contract) {
        return trs.isDirDepositsEnabled(contract);
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is locked.
    boolean isContractLocked(AbstractTRS trs, Address contract) {
        return trs.isContractLocked(contract);
    }

    // Returns true only if the TRS contract whose contract specs are given by specsData is live.
    boolean isContractLive(AbstractTRS trs, Address contract) {
        return trs.isContractLive(contract);
    }

    // Returns the timestamp for this contract.
    long getContractTimestamp(AbstractTRS trs, Address contract) {
        return trs.getTimestamp(contract);
    }

    // Returns true only if account is eligible to use the special one-off withdrawal event.
    boolean accountIsEligibleForSpecial(TRSuseContract trs, Address contract, Address account) {
        return trs.accountIsEligibleForSpecial(contract, account);
    }

    // Returns the last period in which account made a withdrawal or -1 if bad contract or account.
    int getAccountLastWithdrawalPeriod(AbstractTRS trs, Address contract, Address account) {
        return trs.getAccountLastWithdrawalPeriod(contract, account);
    }

    /**
     * Returns the fraction of the total deposits that account owns to 18 decimal places, rounded
     * down.
     *
     * @param accountDepositAmt The amount of deposit funds account has in some contract.
     * @param contractDepositAmt The total amount of deposit funds in that same contract.
     * @return accountDepositAmt / contractDepositAmt.
     */
    BigDecimal computeOwnershipFraction(BigInteger accountDepositAmt, BigInteger contractDepositAmt) {
        //TODO: USE
        return new BigDecimal(accountDepositAmt).
            divide(new BigDecimal(contractDepositAmt), 18, RoundingMode.HALF_DOWN);
    }

    /**
     * Returns the amount of bonus funds owed over the lifetime of some contract to some account.
     *
     * @param fraction The fraction of deposit funds owned by some account in some contract.
     * @param contractBonusAmt The total bonus funds in that same contract.
     * @return the amount of bonus funds owed accordingly over the contract lifetime.
     */
    BigInteger computeBonusOwed(BigDecimal fraction, BigInteger contractBonusAmt) {
        //TODO: USE
        return fraction.multiply(new BigDecimal(contractBonusAmt)).toBigInteger();
    }

    /**
     * Returns the amount of bonus funds available to be collected each period.
     *
     * @param fraction The fraction of deposit funds owned by some account in some contract.
     * @param contractBonusAmt The total bonus funds in that same contract.
     * @param periods The total number of periods that same contract has.
     * @return the amount of bonus funds available to be collected each period.
     */
    BigInteger computeBonusPerPeriod(BigDecimal fraction, BigInteger contractBonusAmt, int periods) {
        //TODO: USE
        return fraction.
            multiply(new BigDecimal(contractBonusAmt)).
            divide(BigDecimal.valueOf(periods), 18, RoundingMode.HALF_DOWN).
            toBigInteger();
    }

    // Returns the share of the bonus tokens account is entitled to withdraw over life of contract.
    BigInteger getBonusShare(AbstractTRS trs, Address contract, Address account) {
        //TODO: replace this with the above 2 methods.
        return trs.computeBonusShare(contract, account);
    }

    /**
     * Returns the amount of extra funds owed over the lifetime of some contract to some account.
     *
     * @param fraction The fraction of deposit funds owned by some account in some contract.
     * @param contractExtraAmt The total extra funds in that same contract.
     * @return the amount of extra funds owed accordingly over the contract lifetime.
     */
    BigInteger computeExtrasOwed(BigDecimal fraction, BigInteger contractExtraAmt) {
        //TODO: USE
        return fraction.multiply(new BigDecimal(contractExtraAmt)).toBigInteger();
    }

    /**
     * Returns the amount of extra funds available to be collected each period.
     *
     * @param fraction The fraction of deposit funds owned by some account in some contract.
     * @param contractBonusAmt The total extra funds in that same contract.
     * @param periods The total number of periods that same contract has.
     * @return the amount of extra funds available to be collected each period.
     */
    BigInteger computeExtrasPerPeriod(BigDecimal fraction, BigInteger contractBonusAmt, int periods) {
        //TODO: USE
        return fraction.
            multiply(new BigDecimal(contractBonusAmt)).
            divide(BigDecimal.valueOf(periods), 18, RoundingMode.HALF_DOWN).
            toBigInteger();
    }

    // Returns the amount of extras account is able to withdraw in the current period.
    BigInteger getExtraShare(AbstractTRS trs, Address contract, Address account, BigDecimal fraction,
        int currPeriod) {
        //TODO: replace this with the above 2 methods.
        return trs.computeExtraFundsToWithdraw(contract, account, fraction, currPeriod);
    }

    /**
     * Returns the amount of deposit funds available to be collected each period.
     *
     * @param depositAmt The amout of deposit funds owned by some account in some contract.
     * @param periods The total number of periods that same contract has.
     * @return the amount of deposit funds available to be collected each period.
     */
    BigInteger computeDepositsPerPeriod(BigInteger depositAmt, int periods) {
        //TODO: USE
        return new BigDecimal(depositAmt).
            divide(BigDecimal.valueOf(periods), 18, RoundingMode.HALF_DOWN).
            toBigInteger();
    }

    // Grabs the current period contract is in as a BigInteger.
    BigInteger getCurrentPeriod(AbstractTRS trs, Address contract) {
        //TODO - this should be computed here, not grabbed from the contract
        return BigInteger.valueOf(getContractCurrentPeriod(trs, contract));
    }

    // Grabs the amount an account can withdraw in special event according to these params.
    BigInteger getSpecialAmount(BigDecimal accBalance, BigDecimal contractBalance,
        BigDecimal bonus, BigDecimal percent) {

        //TODO - this should be computed here, not grabbed from the contract
        BigDecimal owings = new BigDecimal(grabOwings(accBalance, contractBalance, bonus));
        return (owings.multiply(percent.movePointLeft(2))).toBigInteger();
    }

    // Grabs the amount an account can withdraw regularly, not in special event, according to params.
    BigInteger getWithdrawAmt(BigInteger owings, BigInteger specialAmt, int periods) {
        //TODO - this should be computed here, not grabbed from the contract
        BigDecimal periodsBI = new BigDecimal(BigInteger.valueOf(periods));
        BigDecimal owingWithoutSpec = new BigDecimal(owings.subtract(specialAmt));
        BigDecimal res = owingWithoutSpec.divide(periodsBI, 18, RoundingMode.HALF_DOWN);
        return res.toBigInteger();
    }

    // Returns the period that contract is currently in.
    int getContractCurrentPeriod(AbstractTRS trs, Address contract) {
        //TODO - this should be computed here, not grabbed from the contract
        long currTime = blockchain.getBestBlock().getTimestamp();
        return trs.calculatePeriod(contract, trs.getContractSpecs(contract), currTime);
    }

    // Grabs the amount owed to account if the following params are true.
    BigInteger grabOwings(BigDecimal accBalance, BigDecimal contractBalance, BigDecimal bonus) {
        //TODO - this should be computed here, not grabbed from the contract
        BigDecimal fraction = accBalance.divide(contractBalance, 18, RoundingMode.HALF_DOWN);
        BigDecimal share = bonus.multiply(fraction);
        return share.toBigInteger().add(accBalance.toBigInteger());
    }

    // Returns the total amount of tokens account can withdraw over the lifetime of the contract.
    BigInteger getTotalOwed(AbstractTRS trs, Address contract, Address account) {
        //TODO - this should be computed here, not grabbed from the contract
        return trs.computeTotalOwed(contract, account);
    }

    /**
     * Returns the amount an account is expected to receive from a first withdrawal from a contract
     * given that the params are true of the contract and the caller.
     *
     * This method is unreliable if the first withdraw is performed in the last period. There are
     * roundoff errors that accrue and cause the non-final withdrawal amounts to be round down and
     * their sum may be less than the total owed. The contract handles the final period specially to
     * ensure all funds owed are paid OUT.
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
    BigInteger expectedAmtFirstWithdraw(AbstractTRS trs, Address contract, BigInteger deposits,
        BigInteger total, BigInteger bonus, BigDecimal percent, int periods) {

        //TODO - this should be computed here, not grabbed from the contract
        BigInteger currPeriod = getCurrentPeriod(trs, contract);
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger expectedSpecial = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total),
            new BigDecimal(bonus), percent);
        BigInteger expectedWithdraw = currPeriod.multiply(
            getWithdrawAmt(owings, expectedSpecial, periods));
        return expectedWithdraw.add(expectedSpecial);
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
    Address setupContract(int numDepositors, BigInteger deposits, BigInteger bonus,
        int periods, BigDecimal percentage) {

        int precision = percentage.scale();
        BigInteger percent = percentage.movePointRight(precision).toBigInteger();
        Address contract = createTRScontract(AION, true, true, periods,
            percent, precision);

        assertEquals(percentage, getPercentage(newTRSstateContract(AION), contract));
        assertEquals(periods, getPeriods(newTRSstateContract(AION), contract));

        byte[] input = getDepositInput(contract, deposits);
        for (int i = 0; i < numDepositors; i++) {
            Address acc = getNewExistentAccount(deposits);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
        }
        repo.addBalance(contract, bonus);

        lockAndStartContract(contract, AION);
        return contract;
    }

    // Returns an input byte array for the create operation using the provided parameters.
    byte[] getCreateInput(boolean isTest, boolean isDirectDeposit, int periods,
        BigInteger percent, int precision) {

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
            System.arraycopy(percentBytes, 0, input, 14 - percentBytes.length - 1,
                percentBytes.length);
        }
        input[13] = (byte) precision;
        return input;
    }

    // Returns an input byte array for the lock operation using the provided parameters.
    byte[] getLockInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns an input byte array for the start operation using the provided parameters.
    byte[] getStartInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation.
    byte[] getDepositInput(Address contract, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { fail(); }
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length , amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit-for operation.
    byte[] getDepositForInput(Address contract, Address beneficiary, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { fail(); }
        byte[] input = new byte[193];
        input[0] = 0x5;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(beneficiary.toBytes(), 0, input, 33, Address.ADDRESS_LEN);
        System.arraycopy(amtBytes, 0, input, 193 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the withdraw operation.
    byte[] getWithdrawInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = (byte) 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the refund operation.
    byte[] getRefundInput(Address contract, Address account, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { Assert.fail(); }
        byte[] input = new byte[193];
        input[0] = 0x4;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(account.toBytes(), 0, input, 33, Address.ADDRESS_LEN);
        System.arraycopy(amtBytes, 0, input, 193 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isLive (or isStarted) operation.
    byte[] getIsLiveInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isLocked operation.
    byte[] getIsLockedInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x1;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the isDirectDepositEnabled op.
    byte[] getIsDirDepoEnabledInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the period operation.
    byte[] getPeriodInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the periodsAt operation.
    byte[] getPeriodAtInput(Address contract, long blockNum) {
        ByteBuffer buffer = ByteBuffer.allocate(41);
        buffer.put((byte) 0x4);
        buffer.put(contract.toBytes());
        buffer.putLong(blockNum);
        return Arrays.copyOf(buffer.array(), 41);
    }

    // Returns a properly formatted byte array to be used as input for the bulk-withdraw operation.
    byte[] getBulkWithdrawInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the open-funds operation.
    byte[] getOpenFundsInput(Address contract) {
        byte[] input = new byte[33];
        input[0] = 0x3;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the available-for-.. operation.
    byte[] getAvailableForWithdrawalAtInput(Address contract, long timestamp) {
        byte[] input = new byte[41];
        input[0] = 0x5;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(timestamp);
        byte[] buf = buffer.array();
        System.arraycopy(buf, 0, input, input.length - buf.length, buf.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the addExtraFunds operation.
    byte[] getAddExtraInput(Address contract, BigInteger amount) {
        byte[] input = new byte[161];
        input[0] = 0x6;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { fail(); }
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length, amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to add the max extra funds in one operation.
    byte[] getAddExtraMaxInput(Address contract) {
        byte[] input = new byte[161];
        input[0] = 0x6;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        for (int i = 33; i < 161; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a properly formatted byte array to bulk deposit for each account in beneficiaries.
    // This method does: deposit amounts[i] on behalf of beneficiaries[i]
    byte[] getBulkDepositForInput(Address contract, Address[] beneficiaries, BigInteger[] amounts) {
        int len = beneficiaries.length;
        if ((len < 1) || (len > 100)) { fail("Imporper length: " + len); }
        if (len != amounts.length) { fail("beneficiaries and amounts differ in length!"); }
        int arrLen = 33 + (len * 32) + (len * 128);

        byte[] input = new byte[arrLen];
        input[0] = 0x2;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        int index = 33;
        for (int i = 0; i < len; i++) {
            System.arraycopy(beneficiaries[i].toBytes(), 0, input, index, Address.ADDRESS_LEN);
            index += 32 + 128;
            byte[] amtBytes = amounts[i].toByteArray();
            if (amtBytes.length > 128) { fail(); }
            System.arraycopy(amtBytes, 0, input, index - amtBytes.length, amtBytes.length);
        }
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the refund operation, to
    // refund the maximum allowable amount.
    byte[] getMaxRefundInput(Address contract, Address account) {
        byte[] input = new byte[193];
        input[0] = 0x4;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(account.toBytes(), 0, input, 33, Address.ADDRESS_LEN);
        for (int i = 65; i < 193; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the deposit operation, to
    // deposit the maximum allowable amount.
    byte[] getMaxDepositInput(Address contract) {
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        for (int i = 33; i < 161; i++) {
            input[i] = (byte) 0xFF;
        }
        return input;
    }

    // Makes input for numBeneficiaries beneficiaries who each receive a deposit amount deposits.
    byte[] makeBulkDepositForInput(Address contract, int numBeneficiaries, BigInteger deposits) {
        Address[] beneficiaries = new Address[numBeneficiaries];
        BigInteger[] amounts = new BigInteger[numBeneficiaries];
        for (int i = 0; i < numBeneficiaries; i++) {
            beneficiaries[i] = getNewExistentAccount(BigInteger.ZERO);
            amounts[i] = deposits;
        }
        return getBulkDepositForInput(contract, beneficiaries, amounts);
    }

    // Makes input for numOthers beneficiaries and self who each receive a deposit amount deposits.
    byte[] makeBulkDepositForInputwithSelf(Address contract, Address self, int numOthers,
        BigInteger deposits) {

        Address[] beneficiaries = new Address[numOthers + 1];
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
    Set<Address> getAllDepositors(AbstractTRS trs, Address contract) {
        Set<Address> depositors = new HashSet<>();
        Address curr = getLinkedListHead(trs, contract);
        while (curr != null) {
            assertFalse(depositors.contains(curr));
            depositors.add(curr);
            curr = getLinkedListNext(trs, contract, curr);
        }
        return depositors;
    }

    // Returns the head of the list for contract or null if no head.
    Address getLinkedListHead(AbstractTRS trs, Address contract) {
        byte[] head = trs.getListHead(contract);
        if (head == null) { return null; }
        head[0] = (byte) 0xA0;
        return new Address(head);
    }

    // Returns the next account in the linked list after current, or null if no next.
    Address getLinkedListNext(AbstractTRS trs, Address contract, Address current) {
        byte[] next = trs.getListNextBytes(contract, current);
        boolean noNext = (((next[0] & 0x80) == 0x80) || ((next[0] & 0x40) == 0x00));
        if (noNext) { return null; }
        next[0] = (byte) 0xA0;
        return new Address(next);
    }

    // Returns the previous account in the linked list prior to current, or null if no previous.
    Address getLinkedListPrev(AbstractTRS trs, Address contract, Address current) {
        byte[] prev = trs.getListPrev(contract, current);
        if (prev == null) { return null; }
        prev[0] = (byte) 0xA0;
        return new Address(prev);
    }

    // Checks that each account is paid out correctly when they withdraw in a non-final period.
    void checkPayoutsNonFinal(AbstractTRS trs, Address contract, int numDepositors,
        BigInteger deposits, BigInteger bonus, BigDecimal percent, int periods, int currPeriod) {

        Set<Address> contributors = getAllDepositors(trs, contract);
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigDecimal fraction = BigDecimal.ONE.divide(new BigDecimal(numDepositors), 18, RoundingMode.HALF_DOWN);

        for (Address acc : contributors) {
            BigInteger extraShare = getExtraShare(trs, contract, acc, fraction, currPeriod);
            BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits, total, bonus, percent, periods);

            byte[] input = getWithdrawInput(contract);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(amt.add(extraShare), repo.getBalance(acc));
        }
    }

    // Checks that each account is paid out correctly when they withdraw in a final period.
    void checkPayoutsFinal(AbstractTRS trs, Address contract, int numDepositors, BigInteger deposits,
        BigInteger bonus, BigInteger extra) {

        Set<Address> contributors = getAllDepositors(trs, contract);
        BigDecimal fraction = BigDecimal.ONE.divide(new BigDecimal(numDepositors), 18, RoundingMode.HALF_DOWN);
        BigInteger extraShare = fraction.multiply(new BigDecimal(extra)).toBigInteger();
        BigInteger amt = (fraction.multiply(new BigDecimal(bonus))).add(new BigDecimal(deposits)).toBigInteger();
        BigInteger collected = amt.add(extraShare);

        for (Address acc : contributors) {
            byte[] input = getWithdrawInput(contract);
            assertEquals(ResultCode.SUCCESS, newTRSuseContract(acc).execute(input, COST).getResultCode());
            assertEquals(collected, repo.getBalance(acc));
        }

        // Verify that the contract has enough funds to pay OUT.
        BigInteger contractTotal = deposits.multiply(BigInteger.valueOf(numDepositors)).add(bonus).add(extra);
        assertTrue(collected.multiply(BigInteger.valueOf(numDepositors)).compareTo(contractTotal) <= 0);
    }

    // Checks that availableForWithdrawalAt returns expected results for the specified query.
    void checkAvailableForResults(AbstractTRS trs, Address contract, long timestamp,
        int numDepositors, BigInteger deposits, BigInteger bonus, BigDecimal percent, int periods) {
        BigInteger total = deposits.multiply(BigInteger.valueOf(numDepositors));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger amt = expectedAmtFirstWithdraw(trs, contract, deposits,
            total, bonus, percent, periods, timestamp);

        BigDecimal expectedFraction = new BigDecimal(amt).
            divide(new BigDecimal(owings), 18, RoundingMode.HALF_DOWN);

        byte[] input = getAvailableForWithdrawalAtInput(contract, timestamp);
        Set<Address> contributors = getAllDepositors(trs, contract);
        for (Address acc : contributors) {
            ExecutionResult res = newTRSqueryContract(acc).execute(input, COST);
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

    // Returns a new IDataWord that wraps data. Here so we can switch types easy if needed.
    IDataWord newIDataWord(byte[] data) {
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

    /**
     * Returns the amount an account is expected to receive from a first withdrawal from a contract
     * given that the params are true of the contract and the caller.
     *
     * This method uses timestamp to determine the current period the contract is in!
     *
     * This method is unreliable if the first withdraw is performed in the last period. There are
     * roundoff errors that accrue and cause the non-final withdrawal amounts to be round down and
     * their sum may be less than the total owed. The contract handles the final period specially to
     * ensure all funds owed are paid OUT.
     *
     * @param trs An AbstractTRS instance.
     * @param contract The contract in question.
     * @param deposits The amount the caller deposited.
     * @param total The total amount of deposits in the contract.
     * @param bonus The bonus balance in the contract.
     * @param percent The percentage of total owings the caller is eligible to receive in special event.
     * @param periods The number of periods the contract has.
     * @param timestamp Timestamp for the current period the contract is in.
     * @return the expected amount to withdraw on a first call to the contract.
     */
    private BigInteger expectedAmtFirstWithdraw(AbstractTRS trs, Address contract, BigInteger deposits,
        BigInteger total, BigInteger bonus, BigDecimal percent, int periods, long timestamp) {

        //TODO - this should be computed here, not grabbed from the contract
        BigInteger currPeriod = BigInteger.valueOf(trs.calculatePeriod(contract, getContractSpecs(trs, contract), timestamp));
        BigInteger owings = grabOwings(new BigDecimal(deposits), new BigDecimal(total), new BigDecimal(bonus));
        BigInteger expectedSpecial = getSpecialAmount(new BigDecimal(deposits), new BigDecimal(total),
            new BigDecimal(bonus), percent);
        BigInteger expectedWithdraw = currPeriod.multiply(
            getWithdrawAmt(owings, expectedSpecial, periods));
        return expectedWithdraw.add(expectedSpecial);
    }

    // Returns the contract specifications byte array associated with contract.
    private byte[] getContractSpecs(AbstractTRS trs, Address contract) {
        return trs.getContractSpecs(contract);
    }

}
