package org.aion.base;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import org.aion.crypto.ECKey;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;

/** Aion transaction class. */
public class AionTransaction implements Cloneable {

    private final Transaction transaction;
    private final byte[] value;
    private final byte[] nonce;

    /* define transaction type. */
    private final byte type;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    private final byte[] timeStamp;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private final ISignature signature;

    private final byte[] transactionHash;

    // TODO This is used in TxPoolA0, but probably doesn't belong in this class. See AKI-265
    private long energyConsumed = 0;

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

    // Only for creating a transaction from an RLP encoding
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
            long energyConsumed) {

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
        if (energyConsumed < 0) {
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
        this.energyConsumed = energyConsumed;
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
                energyConsumed);
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

    public byte getType() {
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

    public long getEnergyConsumed() {
        return energyConsumed;
    }

    public void setEnergyConsumed(long consumed) {
        energyConsumed = consumed;
    }

    public Transaction getAionTypesTransaction() {
        return transaction;
    }

    @Override
    public String toString() {
        return "TransactionData ["
                + transaction.toString()
                + "hash="
                + ByteUtil.toHexString(transactionHash)
                + ", txType="
                + type
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", signature="
                + ((signature == null) ? "null" : signature.toString())
                + ", energyConsumed="
                + energyConsumed
                + "]";
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(transactionHash) + (int)energyConsumed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof AionTransaction)) {
            return false;
        } else {
            AionTransaction otherObject = (AionTransaction)obj;
            return this.getAionTypesTransaction().equals(otherObject.getAionTypesTransaction())
                && Arrays.equals(this.value, otherObject.value)
                && Arrays.equals(this.nonce, otherObject.nonce)
                && this.energyConsumed == otherObject.energyConsumed
                && this.type == otherObject.type
                && Arrays.equals(this.timeStamp, otherObject.timeStamp)
                && Arrays.equals(this.transactionHash, otherObject.transactionHash)
                && Objects.equals(this.signature, otherObject.signature);
        }
    }
}
