package org.aion.avm.version2.contracts;

import avm.Blockchain;

public class TransactionHash {

    public static byte[] main() {
        return Blockchain.getTransactionHash();
    }
}
