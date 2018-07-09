package org.aion.precompiled.TRS;

import static junit.framework.TestCase.fail;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.contracts.TRS.TRSownerContract;
import org.aion.precompiled.contracts.TRS.TRSqueryContract;
import org.aion.precompiled.contracts.TRS.TRSuseContract;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.core.IAionBlockchain;
import org.junit.Assert;

/**
 * A class exposing commonly used helper methods and variables for TRS-related testing.
 */
class TRShelpers {
    Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    long COST = 21000L;
    IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    IAionBlockchain blockchain = StandaloneBlockchain.inst();
    List<Address> tempAddrs;
    private static final byte[] out = new byte[1];

    int ownerMaxOp = 4;     //TODO remove this stuff..
    int ownerCurrMaxOp = 2; // remove once this hits ownerMaxOp
    int useMaxOp = 6;
    int useCurrMaxOp = 0;   // remove once this hits useMaxOp

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

    // Returns a new TRSownerContract that calls the contract using caller.
    TRSownerContract newTRSownerContract(Address caller) {
        return new TRSownerContract(repo, caller, blockchain);
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
        TRSownerContract trs = new TRSownerContract(repo, owner, blockchain);
        Address contract = new Address(trs.execute(input, COST).getOutput());
        tempAddrs.add(contract);
        repo.incrementNonce(owner);
        repo.flush();
        return contract;
    }

    // Returns the address of a newly created TRS contract and locks it; assumes all params valid.
    Address createAndLockTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getLockInput(contract);
        if (!newTRSownerContract(owner).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        return contract;
    }

    // Returns the address of a newly created TRS contract that is locked and live.
    Address createLockedAndLiveTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);
        byte[] input = getLockInput(contract);
        if (!newTRSownerContract(owner).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to lock contract!");
        }
        input = getStartInput(contract);
        if (!newTRSownerContract(owner).execute(input, COST).getCode().equals(ResultCode.SUCCESS)) {
            fail("Failed to start contract!");
        }
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
        if (amtBytes.length > 128) { Assert.fail(); }
        byte[] input = new byte[161];
        input[0] = 0x0;
        System.arraycopy(contract.toBytes(), 0, input, 1, Address.ADDRESS_LEN);
        System.arraycopy(amtBytes, 0, input, 161 - amtBytes.length , amtBytes.length);
        return input;
    }

    // Returns a properly formatted byte array to be used as input for the refund operation.
    byte[] getRefundInput(Address contract, Address account, BigInteger amount) {
        byte[] amtBytes = amount.toByteArray();
        if (amtBytes.length > 128) { Assert.fail(); }
        byte[] input = new byte[193];
        input[0] = 0x5;
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

    // Returns a properly formatted byte array to be used as input for the periodsAt operation.
    byte[] getPeriodAtInput(Address contract, long blockNum) {
        ByteBuffer buffer = ByteBuffer.allocate(41);
        buffer.put((byte) 0x4);
        buffer.put(contract.toBytes());
        buffer.putLong(blockNum);
        return Arrays.copyOf(buffer.array(), 41);
    }

    // Returns a byte array signalling false for a TRS contract query operation result.
    byte[] getFalseContractOutput() {
        out[0] = 0x0;
        return out;
    }

    // Returns a byte array signalling true for a TRS contract query operation result.
    byte[] getTrueContractOutput() {
        out[0] = 0x1;
        return out;
    }

    // Returns a DataWord that is the key corresponding to the contract specifications in storage.
    //TODO remove
    IDataWord getSpecKey() {
        byte[] specsKey = new byte[DataWord.BYTES];
        specsKey[0] = (byte) 0xE0;
        return new DataWord(specsKey);
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

}
