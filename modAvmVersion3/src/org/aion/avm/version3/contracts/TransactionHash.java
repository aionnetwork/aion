package org.aion.avm.version3.contracts;

import avm.Blockchain;

public class TransactionHash {

    public static byte[] main() {
        return Blockchain.getTransactionHash();
    }
}
