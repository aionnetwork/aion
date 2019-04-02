package org.aion.zero.impl.vm.contracts;

import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.userlib.abi.ABIDecoder;

public final class Contract {
    private static final Address owner = BlockchainRuntime.getCaller();

    public static boolean isOwner() {
        return BlockchainRuntime.getCaller().equals(owner);
    }

    public static void transfer(Address address) {
        BlockchainRuntime.call(address, BlockchainRuntime.getValue(), new byte[0], BlockchainRuntime.getRemainingEnergy());
    }

    public static void output() {
        BlockchainRuntime.println("Owner is: " + owner);
        BlockchainRuntime.log(owner.unwrap(), "message".getBytes());
    }

    public static byte[] main() {
        byte[] inputBytes = BlockchainRuntime.getData();
        String methodName = ABIDecoder.decodeMethodName(inputBytes);
        if (methodName == null) {
            return new byte[0];
        } else {
            Object[] argValues = ABIDecoder.decodeArguments(inputBytes);
            if (methodName.equals("transfer")) {
                transfer((Address) argValues[0]);
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }

}