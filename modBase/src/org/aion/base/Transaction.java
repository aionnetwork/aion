package org.aion.base;

import java.math.BigInteger;

public interface Transaction extends Cloneable, TransactionInterface {

    byte[] getEncoded();

    void setEncoded(byte[] _encodedData);

    BigInteger getNonceBI();

    BigInteger getTimeStampBI();

    Transaction clone();

    long getNrgConsume();

    void setNrgConsume(long _nrg);
}
