package org.aion.zero.impl.vm.contracts;

import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.userlib.abi.ABIDecoder;

public class AvmHelloWorld {

    public static void sayHello() {
        BlockchainRuntime.println("Hello World!");
    }

    public static byte[] main() {
        byte[] inputBytes = BlockchainRuntime.getData();
        String methodName = ABIDecoder.decodeMethodName(inputBytes);
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("sayHello")) {
                sayHello();
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }
}
