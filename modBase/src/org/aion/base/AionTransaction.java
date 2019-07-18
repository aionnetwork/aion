package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;

/** Aion transaction class. */
public class AionTransaction implements Cloneable {

    public final Transaction transaction;
    public final byte[] value;
    public final byte[] nonce;

    /* define transaction type. */
    private final byte type;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    private final byte[] timeStamp;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private final ISignature signature;

    private final byte[] transactionHash;

    // TODO This is used in TxPoolA0, but probably doesn't belong in this class. See AKI-265
    private long nrgConsume = 0;

    public AionTransaction(
            ECKey key,
            byte[] nonce,
            AionAddress destinationAddress,
            byte[] value,
            byte[] transactionData,
            long energyLimit,
            long energyPrice) {

        this(
                key,
                nonce,
                destinationAddress,
                value,
                transactionData,
                energyLimit,
                energyPrice,
                TransactionTypes.DEFAULT);
    }

    // constructor for explicitly setting a transaction type.
    public AionTransaction(
            ECKey key,
            byte[] nonce,
            AionAddress destinationAddress,
            byte[] value,
            byte[] transactionData,
            long energyLimit,
            long energyPrice,
            byte txType) {

        this(
                key,
                nonce,
                destinationAddress,
                value,
                transactionData,
                energyLimit,
                energyPrice,
                txType,
                ByteUtil.longToBytes(TimeInstant.now().toEpochMicro()));
    }

    // constructor for testing to explicitly set the timestamp
    public AionTransaction(
        ECKey key,
        byte[] nonce,
        AionAddress destinationAddress,
        byte[] value,
        byte[] transactionData,
        long energyLimit,
        long energyPrice,
        byte txType,
        byte[] timeStamp) {

        if (key == null) {
            throw new NullPointerException("No Key");
        }
        if (timeStamp == null) {
            throw new NullPointerException("No Timestamp");
        }

        if (destinationAddress == null) {
            this.transaction =
                    Transaction.contractCreateTransaction(
                            new AionAddress(key.getAddress()),
                            new BigInteger(1, nonce),
                            new BigInteger(1, value),
                            transactionData,
                            energyLimit,
                            energyPrice);
        } else {
            this.transaction =
                    Transaction.contractCallTransaction(
                            new AionAddress(key.getAddress()),
                            destinationAddress,
                            new BigInteger(1, nonce),
                            new BigInteger(1, value),
                            transactionData,
                            energyLimit,
                            energyPrice);
        }

        this.nonce = nonce;
        this.value = value;
        this.type = txType;
        this.timeStamp = timeStamp;
        this.signature = key.sign(TransactionUtil.hashWithoutSignature(this));
        this.transactionHash = TransactionUtil.hashTransaction(this); // This has to come last
    }

    // Only for creating a transaction from and RLP encoding
    public AionTransaction(
            byte[] nonce,
            AionAddress senderAddress,
            AionAddress destinationAddress,
            byte[] value,
            byte[] transactionData,
            long energyLimit,
            long energyPrice,
            byte txType,
            ISignature signature,
            byte[] timeStamp,
            long nrgConsume) {

        if (nonce == null) {
            throw new NullPointerException("No Nonce");
        }
        if (senderAddress == null) {
            throw new NullPointerException("No Sender");
        }
        if (value == null) {
            throw new NullPointerException("No Value");
        }
        if (transactionData == null) {
            throw new NullPointerException("No data");
        }
        if (signature == null) {
            throw new NullPointerException("No Signature");
        }
        if (timeStamp == null) {
            throw new NullPointerException("No Timestamp");
        }
        if (nrgConsume < 0) {
            throw new IllegalArgumentException("Negative energyConsumed");
        }

        if (destinationAddress == null) {
            this.transaction =
                    Transaction.contractCreateTransaction(
                            senderAddress,
                            new BigInteger(1, nonce),
                            new BigInteger(1, value),
                            transactionData,
                            energyLimit,
                            energyPrice);
        } else {
            this.transaction =
                    Transaction.contractCallTransaction(
                            senderAddress,
                            destinationAddress,
                            new BigInteger(1, nonce),
                            new BigInteger(1, value),
                            transactionData,
                            energyLimit,
                            energyPrice);
        }

        this.nonce = nonce;
        this.value = value;
        this.type = txType;
        this.signature = signature;
        this.timeStamp = timeStamp;
        this.nrgConsume = nrgConsume;
        this.transactionHash = TransactionUtil.hashTransaction(this);
    }

    @Override
    public AionTransaction clone() {
        return new AionTransaction(
                nonce,
                getSenderAddress(),
                getDestinationAddress(),
                value,
                getData(),
                getEnergyLimit(),
                getEnergyPrice(),
                type,
                signature,
                timeStamp,
                nrgConsume);
    }

    public byte[] getTransactionHash() {
        return transactionHash;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getValue() {
        return value;
    }

    public BigInteger getNonceBI() {
        return transaction.nonce;
    }

    public BigInteger getValueBI() {
        return transaction.value;
    }

    public byte[] getTimestamp() {
        return timeStamp;
    }

    public BigInteger getTimeStampBI() {
        return new BigInteger(1, getTimestamp());
    }

    public long getEnergyLimit() {
        return transaction.energyLimit;
    }

    public long getEnergyPrice() {
        return transaction.energyPrice;
    }

    public AionAddress getDestinationAddress() {
        return transaction.destinationAddress;
    }

    public byte[] getData() {
        return transaction.copyOfTransactionData();
    }

    public byte getTargetVM() {
        return type;
    }

    public ISignature getSignature() {
        return signature;
    }

    public boolean isContractCreationTransaction() {
        return transaction.isCreate;
    }

    public AionAddress getSenderAddress() {
        return transaction.senderAddress;
    }

    public long nrgPrice() {
        return transaction.energyPrice;
    }

    public long nrgLimit() {
        return transaction.energyLimit;
    }

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }

    @Override
    public String toString() {
        return "TransactionData ["
                + transaction.toString()
                + "hash="
                + ByteUtil.toHexString(getTransactionHash())
                + ", txType="
                + type
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", sig="
                + ((signature == null) ? "null" : signature.toString())
                + "]";
    }

    // TODO is there a reason for this to be different than the transactionHash?
    @Override
    public int hashCode() {

        byte[] hash = this.getTransactionHash();
        int hashCode = 0;

        for (int i = 0; i < hash.length; ++i) {
            hashCode += hash[i] * i;
        }

        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof AionTransaction)) {
            return false;
        }
        AionTransaction tx = (AionTransaction) obj;

        return tx.hashCode() == this.hashCode();
    }
}
