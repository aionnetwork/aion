package org.aion.avm.version2.contracts;

import avm.Blockchain;
import org.aion.avm.userlib.abi.ABIDecoder;

public class HelloWorld {

    public static void sayHello() {
        Blockchain.println("Hello World!");
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
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
