package org.aion.base.type;

import java.math.BigInteger;
import org.aion.vm.api.interfaces.TransactionInterface;

/** @author jin */
public interface ITransaction extends Cloneable, TransactionInterface {

    byte[] getEncoded();

    BigInteger getNonceBI();

    BigInteger getTimeStampBI();

    ITransaction clone();

    long getNrgConsume();

    void setEncoded(byte[] _encodedData);

    void setNrgConsume(long _nrg);
}
