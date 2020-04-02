package org.aion.avm.version1.contracts;

import avm.Address;
import avm.Blockchain;
import java.math.BigInteger;
import org.aion.avm.userlib.abi.ABIDecoder;

/**
 * This contract can be called for testing functionality relating to deployments of contracts as internal transactions.
 */
public class DeployAsInternalTransaction {

    static Address deployer;

    static {
        deployer = new Address(Blockchain.getCaller().toByteArray());
    }

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("deployAndRequireSuccess")) {
                deployAndRequireSuccess(decoder.decodeOneByteArray(), decoder.decodeOneLong());
                return new byte[0];
            } else if (methodName.equals("deploy")) {
                deploy(decoder.decodeOneByteArray(), decoder.decodeOneLong());
                return new byte[0];
            } else {
                return new byte[0];
            }
        }
    }

    public static void deployAndRequireSuccess(byte[] data, long energyLimit) {
        Blockchain.require(Blockchain.create(BigInteger.ZERO, data, energyLimit).isSuccess());
    }

    public static void deploy(byte[] data, long energyLimit) {
        Blockchain.create(BigInteger.ZERO, data, energyLimit);
    }
}
