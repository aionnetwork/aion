package org.aion.factort;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.generic.ITransactionFactory;
import org.aion.zero.types.AionTransaction;

import java.math.BigInteger;

public class AionTransactionFactory implements ITransactionFactory<AionTransaction> {
    @Override
    public AionTransaction createTransaction(BigInteger nonce, Address to, BigInteger value, byte[] data) {
        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);
        return new AionTransaction(nonceBytes, to, valueBytes, data);
    }

    @Override
    public AionTransaction createTransaction(byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice) {
        return new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
    }

    @Override
    public AionTransaction createTransaction(byte[] encodedTransaction) {
        return new AionTransaction(encodedTransaction);
    }

    @Override
    public AionTransaction createTransaction(byte[] nonce, Address from, Address to, byte[] value, byte[] data, long nrg, long nrgPrice) {
        return new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);
    }
}
