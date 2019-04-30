package org.aion.zero.impl.vm.contracts;

import avm.Blockchain;
import avm.Result;
import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

public class AvmInternalTx {

    private static int value;

    public static int recursivelyGetValue() {
        byte[] args = ABIEncoder.encodeOneString("getValue");
        Result result = Blockchain.call(Blockchain.getAddress(), BigInteger.ZERO, args, Blockchain.getRemainingEnergy());
        Blockchain.require(result.isSuccess());
        ABIDecoder decoder = new ABIDecoder(result.getReturnData());
        return decoder.decodeOneInteger();
    }

    public static int getValue() {
        return value;
    }

    public static void setValue(int val) {
        value = val;
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("getValue")) {
                return ABIEncoder.encodeOneInteger(getValue());
            } else if (methodName.equals("setValue")) {
                setValue(decoder.decodeOneInteger());
                return new byte[0];
            } else if (methodName.equals("recursivelyGetValue")) {
                return ABIEncoder.encodeOneInteger(recursivelyGetValue());
            } else {
                return new byte[0];
            }
        }
    }
}
