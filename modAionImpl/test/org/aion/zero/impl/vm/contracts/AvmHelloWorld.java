package org.aion.zero.impl.vm.contracts;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.BlockchainRuntime;

public class AvmHelloWorld {

    public static void sayHello() {
        BlockchainRuntime.println("Hello World!");
    }

    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithClass(AvmHelloWorld.class, BlockchainRuntime.getData());
    }

}
