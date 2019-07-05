package org.aion.base;

import java.math.BigInteger;
import org.aion.types.AionAddress;

public interface Transaction extends Cloneable {

    AionAddress getSenderAddress();

    AionAddress getDestinationAddress();

    AionAddress getContractAddress();

    byte[] getTransactionHash();
    byte[] getNonce();
    byte[] getValue();
    byte[] getData();
    byte[] getTimestamp();
    byte[] getEncoded();

    byte getTargetVM();
    byte getKind();

    long getEnergyLimit();
    long getEnergyPrice();
    long getNrgConsume();
    long getTransactionCost();

    boolean isContractCreationTransaction();

    BigInteger getNonceBI();
    BigInteger getTimeStampBI();

    Transaction clone();

    void setNrgConsume(long _nrg);
    void setEncoded(byte[] _encodedData);
}
