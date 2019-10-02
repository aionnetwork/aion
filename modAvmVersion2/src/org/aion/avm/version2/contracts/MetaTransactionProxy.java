package org.aion.avm.version2.contracts;

import avm.Blockchain;
import avm.Result;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

/**
 * A test of the AIP #044 meta-transaction functionality.
 */
public class MetaTransactionProxy {
    private static byte[] transaction;

    public static byte[] main() {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String methodName = decoder.decodeMethodName();
        if (methodName == null) {
            return new byte[0];
        } else {
            if (methodName.equals("store")) {
                return ABIEncoder.encodeOneByteArray(store(decoder.decodeOneByteArray()));
            } else if (methodName.equals("call")) {
                return ABIEncoder.encodeOneByteArray(call());
            } else if (methodName.equals("callInline")) {
                return ABIEncoder.encodeOneByteArray(callInline(decoder.decodeOneByteArray()));
            } else if (methodName.equals("createInline")) {
                return ABIEncoder.encodeOneByteArray(createInline(decoder.decodeOneByteArray()));
            } else if (methodName.equals("identity")) {
                return ABIEncoder.encodeOneByteArray(identity(decoder.decodeOneByteArray()));
            } else {
                return new byte[0];
            }
        }
    }

    public static byte[] store(byte[] transaction) {
        MetaTransactionProxy.transaction = transaction;
        // We will return a 1-element array on successful store.
        return new byte[] { 42 };
    }

    public static byte[] call() {
        return doCall(MetaTransactionProxy.transaction);
    }

    public static byte[] callInline(byte[] transaction) {
        return doCall(transaction);
    }

    public static byte[] createInline(byte[] transaction) {
        return doCreate(transaction);
    }

    public static byte[] identity(byte[] data) {
        return data;
    }


    private static byte[] doCall(byte[] transaction) {
        Result result = Blockchain.invokeTransaction(transaction, Blockchain.getEnergyLimit());
        byte[] response = null;
        if (result.isSuccess()) {
            byte[] data = result.getReturnData();
            response = (null != data)
                ? new ABIDecoder(data).decodeOneByteArray()
                : null;
        }
        return response;
    }

    private static byte[] doCreate(byte[] transaction) {
        Result result = Blockchain.invokeTransaction(transaction, Blockchain.getEnergyLimit());
        return (result.isSuccess())
            ? result.getReturnData()
            : null;
    }
}
