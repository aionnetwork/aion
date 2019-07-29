package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;

public class PooledTransaction {
    private final AionTransaction aionTransaction;
    private final long energyConsumed;

    public PooledTransaction(AionTransaction aionTransaction, long energyConsumed) {
        this.aionTransaction = aionTransaction;
        this.energyConsumed = energyConsumed;
    }

    public byte[] getTransactionHash() {
        return aionTransaction.getTransactionHash();
    }

    public byte[] getNonce() {
        return aionTransaction.getNonce();
    }

    public byte[] getValue() {
        return aionTransaction.getValue();
    }

    public BigInteger getNonceBI() {
        return aionTransaction.getNonceBI();
    }

    public BigInteger getValueBI() {
        return aionTransaction.getValueBI();
    }

    public byte[] getTimestamp() {
        return aionTransaction.getTimestamp();
    }

    public BigInteger getTimeStampBI() {
        return aionTransaction.getTimeStampBI();
    }

    public long getEnergyLimit() {
        return aionTransaction.getEnergyLimit();
    }

    public long getEnergyPrice() {
        return aionTransaction.getEnergyPrice();
    }

    public AionAddress getDestinationAddress() {
        return aionTransaction.getDestinationAddress();
    }

    public byte[] getData() {
        return aionTransaction.getData();
    }

    public byte getType() {
        return aionTransaction.getType();
    }

    public ISignature getSignature() {
        return aionTransaction.getSignature();
    }

    public boolean isContractCreationTransaction() {
        return aionTransaction.isContractCreationTransaction();
    }

    public AionAddress getSenderAddress() {
        return aionTransaction.getSenderAddress();
    }

    public long getEnergyConsumed() {
        return energyConsumed;
    }

    public AionTransaction getAionTransaction() {
        return aionTransaction;
    }
}
