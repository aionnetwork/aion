package org.aion.zero.impl.vm.contracts;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.abi.ABIDecoder;

public final class Contract {
    private static final Address owner = Blockchain.getCaller();

    public static boolean isOwner() {
        return Blockchain.getCaller().equals(owner);
    }

    public static void transfer(Address address) {
        Blockchain.call(address, Blockchain.getValue(), new byte[0], Blockchain.getRemainingEnergy());
    }

    public static void output() {
        Blockchain.println("Owner is: " + owner);
        Blockchain.log(owner.unwrap(), "message".getBytes());
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("transfer")) {
                transfer(decoder.decodeOneAddress());
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }

}