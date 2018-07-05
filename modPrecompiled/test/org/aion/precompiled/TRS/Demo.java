package org.aion.precompiled.TRS;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.TRS.AbstractTRS;
import org.aion.precompiled.contracts.TRS.TRSownerContract;
import org.aion.precompiled.contracts.TRS.TRSuseContract;

public class Demo {
    private static final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> repo = new DummyRepo();
    private static final BigInteger DEFAULT_BALANCE = new BigInteger("2387956123");

    private static Address createAccount() {
        Address owner = new Address(ECKeyFac.inst().create().getAddress());
        repo.createAccount(owner);
        repo.addBalance(owner, DEFAULT_BALANCE);
        repo.flush();
        return owner;
    }

    private static void printAddressShort(Address addr) {
        byte[] shortAddr = Arrays.copyOf(addr.toBytes(), 8);
        System.out.print(ByteUtil.toHexString(shortAddr) + "..");
    }

    private static Address listNext(AbstractTRS trs, Address contract, Address curr) {
        byte[] next = trs.getListNext(contract, curr);
        next[0] = (byte) 0xA0;
        return new Address(next);
    }

    private static Address listHead(AbstractTRS trs, Address contract) {
        byte[] head = trs.getListHead(contract);
        head[0] = (byte) 0xA0;
        return new Address(head);
    }

    public static void main(String[] args) {
        ((DummyRepo) repo).storageErrorReturn = null;
        TRShelpers helper = new TRShelpers();
        Set<Address> cleanup = new HashSet<>();

        // 1. Create a new public-facing TRS contract. 10 periods and 0.2% one-off withdrawal amount.
        System.out.println("Creating new public-facing TRS contract...");
        Address owner = createAccount();
        byte[] input = helper.getCreateInput(false, true, 10, BigInteger.TWO, 1);
        AbstractTRS trs = new TRSownerContract(repo, owner);
        Address contract = new Address(trs.execute(input, 21_000L).getOutput());

        System.out.println("New TRS contract with address: " + contract + " and owner: " + owner);
        System.out.println("Contract's current total balance: " + trs.getTotalBalance(contract));
        cleanup.add(owner);
        cleanup.add(contract);
        System.out.println("");

        // 2. Have some accounts deposit into the contract.
        System.out.println("Some accounts deposit into the contract...");
        input = helper.getDepositInput(contract, DEFAULT_BALANCE);
        int numAccs = 10;
        System.out.println(numAccs + " accounts are depositing " + DEFAULT_BALANCE + " into the contract.");
        for (int i = 0; i < numAccs; i++) {
            Address acc = createAccount();
            trs = new TRSuseContract(repo, acc);
            trs.execute(input, 21_000L);

            if (!repo.getBalance(acc).equals(BigInteger.ZERO)) {
                System.out.println("Error: depositor still has funds left!");
            }
            if (!trs.getDepositBalance(contract, acc).equals(DEFAULT_BALANCE)) {
                System.out.println("Error: depositor did not deposit the correct amount!");
            }
            cleanup.add(acc);
        }

        BigInteger expected = DEFAULT_BALANCE.multiply(BigInteger.valueOf(numAccs));
        BigInteger total = trs.getTotalBalance(contract);
        System.out.println("We expect the total contract balance to be: " + expected);
        System.out.println("Contract's current total balance is: " + total);
        if (!expected.equals(total)) {
            System.out.println("Error: amount differed from what was expected!");
        }
        System.out.println("");

        // 3. Display the linked list format of the storage.
        System.out.println("The accounts in the contract...");
        Set<Address> refunders = new HashSet<>();

        Address curr = listHead(trs, contract);
        printAddressShort(curr);
        for (int i = 1; i < numAccs; i++) {
            if (i % 3 == 0) { refunders.add(curr); }
            System.out.print(" -> ");
            curr = listNext(trs, contract, curr);
            printAddressShort(curr);
        }
        System.out.println(" -> " + trs.getListNext(contract, curr));
        System.out.println("");

        // 4. Perform some refunds.
        System.out.println("The owner refunds some accounts...");
        trs = new TRSuseContract(repo, owner);
        for (Address acc : refunders) {
            System.out.print("Refunding account ");
            printAddressShort(acc);
            System.out.print(" for amount: " + DEFAULT_BALANCE);

            input = helper.getRefundInput(contract, acc, DEFAULT_BALANCE);
            trs.execute(input, 21_000L);

            System.out.println(". That account now has a balance of: " + repo.getBalance(acc) + ".");
        }

        expected = expected.subtract(DEFAULT_BALANCE.multiply(BigInteger.valueOf(refunders.size())));
        total = trs.getTotalBalance(contract);
        System.out.println("We expect the total contract balance to be: " + expected);
        System.out.println("Conract's total balance is: " + total);
        if (!expected.equals(total)) {
            System.out.println("Error: amount differed from what was expected!");
        }
        System.out.println("");

        // 5. Display the linked list's new state.
        System.out.println("The linked list now looks like this...");
        curr = listHead(trs, contract);
        printAddressShort(curr);
        for (int i = 1; i < numAccs - refunders.size(); i++) {
            System.out.print(" - > ");
            curr = listNext(trs, contract, curr);
            printAddressShort(curr);
            if (refunders.contains(curr)) {
                System.out.print("Error: one of the refunders is still present in the list!");
            }
        }
        System.out.println(" -> " + trs.getListNext(contract, curr));
        System.out.println("");

        // 5. Lock the contract.
        System.out.println("The owner now locks the contract...");
        input = helper.getLockInput(contract);
        trs = new TRSownerContract(repo, owner);
        trs.execute(input, 21_000L);

        System.out.println("Owner tries to refund the account at the head of the list...");
        input = helper.getRefundInput(contract, listHead(trs, contract), BigInteger.ONE);
        if (trs.execute(input, 21_000L).getCode().equals(ResultCode.INTERNAL_ERROR)) {
            System.out.println("Owner is unable to refund since contract is locked.");
        }

        // Clean up the accounts we created...
        for (Address acct : cleanup) {
            repo.deleteAccount(acct);
        }
        repo.flush();
    }

}
