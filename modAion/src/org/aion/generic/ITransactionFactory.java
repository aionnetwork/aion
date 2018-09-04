package org.aion.generic;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;

import java.math.BigInteger;

public interface ITransactionFactory<TX extends ITransaction> {
    TX createTransaction(BigInteger nonce, Address to, BigInteger value, byte[] data);

    TX createTransaction(byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice);

    TX createTransaction(byte[] encodedTransaction);

    TX createTransaction(byte[] nonce, Address from, Address to, byte[] value, byte[] data, long nrg, long nrgPrice);
}
