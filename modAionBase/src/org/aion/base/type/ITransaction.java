package org.aion.base.type;

import java.math.BigInteger;

/** @author jin */
public interface ITransaction extends Cloneable {

    byte[] getHash();

    byte[] getEncoded();

    AionAddress getFrom();

    AionAddress getTo();

    byte[] getNonce();

    BigInteger getNonceBI();

    byte[] getTimeStamp();

    BigInteger getTimeStampBI();

    /**
     * Added these two interfaces with refactoring (should be here in the first place!)
     *
     * @return
     */
    byte[] getValue();

    byte[] getData();

    byte getType();

    ITransaction clone();

    long getNrg();

    long getNrgPrice();

    long getNrgConsume();

    void setEncoded(byte[] _encodedData);

    void setNrgConsume(long _nrg);

    boolean isContractCreation();
}
