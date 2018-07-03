package org.aion.precompiled.TRS;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.contracts.TRS.TRSownerContract;

/**
 * A class exposing commonly used helper methods and variables for TRS-related testing.
 */
class TRShelpers {
    Address AION = Address.wrap("0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    long COST = 21000L;
    IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo;
    List<Address> tempAddrs;
    int ownerMaxOp = 4;
    int ownerCurrMaxOp = 2; // remove once this hits ownerMaxOp
    int useMaxOp = 6;
    int useCurrMaxOp = 0;   // remove once this hits useMaxOp

    // Returns a new account with initial balance balance that exists in the repo.
    Address getNewExistentAccount(BigInteger balance) {
        Address acct = Address.wrap(ECKeyFac.inst().create().getAddress());
        repo.createAccount(acct);
        repo.addBalance(acct, balance);
        repo.flush();
        tempAddrs.add(acct);
        return acct;
    }

    // Returns the address of a newly created TRS contract, assumes all params are valid.
    Address createTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        byte[] input = getCreateInput(isTest, isDirectDeposit, periods, percent, precision);
        TRSownerContract trs = new TRSownerContract(repo, owner);
        Address contract = new Address(trs.execute(input, COST).getOutput());
        tempAddrs.add(contract);
        return contract;
    }

    // Returns the address of a newly created TRS contract and locks it; assumes all params valid.
    Address createAndLockTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);

        IDataWord specs = repo.getStorageValue(contract, getSpecKey());
        byte[] specsBytes = specs.getData();
        specsBytes[14] = (byte) 0x1;

        repo.addStorageRow(contract, getSpecKey(), newIDataWord(specsBytes));
        repo.flush();
        return contract;
    }

    // Returns the address of a newly created TRS contract that is locked and live.
    Address createLockedAndLiveTRScontract(Address owner, boolean isTest, boolean isDirectDeposit,
        int periods, BigInteger percent, int precision) {

        Address contract = createTRScontract(owner, isTest, isDirectDeposit, periods, percent, precision);

        IDataWord specs = repo.getStorageValue(contract, getSpecKey());
        byte[] specsBytes = specs.getData();
        specsBytes[14] = (byte) 0x1;
        specsBytes[15] = (byte) 0x1;

        repo.addStorageRow(contract, getSpecKey(), newIDataWord(specsBytes));
        repo.flush();
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

    // Returns a DataWord that is the key corresponding to the contract specifications in storage.
    IDataWord getSpecKey() {
        byte[] specsKey = new byte[DoubleDataWord.BYTES];
        specsKey[0] = (byte) 0xE0;
        return newIDataWord(specsKey);
    }

    // Returns a new IDataWord that wraps data. Here so we can switch types easy if needed.
    IDataWord newIDataWord(byte[] data) {
        return new DoubleDataWord(data);
    }

}
