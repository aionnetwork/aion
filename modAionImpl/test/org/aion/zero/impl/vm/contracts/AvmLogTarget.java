package org.aion.zero.impl.vm.contracts;

import avm.Address;
import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;

public class AvmLogTarget {
    public static byte[] data = new byte[]{ 0, 1, 1, 2, 3, 2 };
    public static byte[] topic1 = new byte[]{ 0, 1, 1, 2, 3, 2, 7, 7, 7, 3 };
    public static byte[] topic2 = new byte[]{ 0, 1, 1, 2, 3, 2, 0, 0, 1 };

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String method = decoder.decodeMethodName();

        if (method.equals("fireLogsOnSuccess")) {
            fireLogsOnSuccess(decoder.decodeOneAddress());
        } else if (method.equals("fireLogsAndFail")) {
            fireLogsAndFail(decoder.decodeOneAddress());
        }

        return null;
    }

    public static void fireLogsOnSuccess(Address address) {
        Blockchain.log(data);
        Blockchain.log(topic1, data);
        Blockchain.log(topic1, topic2, data);
        Blockchain.call(address, BigInteger.ZERO, new byte[0], Blockchain.getRemainingEnergy());
    }

    public static void fireLogsAndFail(Address address) {
        Blockchain.log(data);
        Blockchain.log(topic1, data);
        Blockchain.log(topic1, topic2, data);
        Blockchain.call(address, BigInteger.ZERO, new byte[0], Blockchain.getRemainingEnergy());
        Blockchain.require(false);
    }
}
