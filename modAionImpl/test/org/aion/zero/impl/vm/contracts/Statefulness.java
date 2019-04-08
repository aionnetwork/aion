package org.aion.zero.impl.vm.contracts;

import avm.Address;
import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

public class Statefulness {
    private static int counter = 0;

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("transferValue")) {
                transferValue(decoder.decodeOneByteArray(), decoder.decodeOneLong());
                return new byte[0];
            } else if (methodName.equals("getCount")) {
                return ABIEncoder.encodeOneInteger(getCount());
            } else if (methodName.equals("incrementCounter")) {
                incrementCounter();
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }

    public static void transferValue(byte[] beneficiary, long amount) {
        Address recipient = new Address(beneficiary);
        if (Blockchain.call(recipient, BigInteger.valueOf(amount), new byte[0], Blockchain.getRemainingEnergy()).isSuccess()) {
            Blockchain.println("Transfer was a success. "
                + "Beneficiary balance = " + Blockchain.getBalance(recipient)
                + ", Contract balance = " + Blockchain.getBalance(Blockchain.getAddress()));
        } else {
            Blockchain.println("Transfer was unsuccessful.");
        }
        counter++;
    }

    public static void incrementCounter() {
        counter++;
    }

    public static int getCount() {
        Blockchain.println("Count = " + counter);
        return counter;
    }

    public static long getContractBalance() {
        BigInteger balance =  Blockchain.getBalance(Blockchain.getAddress());
        Blockchain.println("Contract balance = " + balance);
        counter++;
        return balance.longValue();
    }

    public static long getBalanceOf(Address address) {
        BigInteger balance =  Blockchain.getBalance(address);
        Blockchain.println("Balance of " + address + " = " + balance);
        counter++;
        return balance.longValue();
    }
}
