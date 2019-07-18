package org.aion.base;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.util.bytes.ByteUtil;

/** Aion transaction class. */
public class AionTransaction {

    private final Transaction transaction;

    /** Value is kept as a byte-array to prevent conversion problems */
    private final byte[] value;

    /** Nonce is kept as a byte-array to prevent conversion problems */
    private final byte[] nonce;

    /** AVM_CREATE_CODE for AVM contract creation. DEFAULT otherwise */
    private final byte type;

    /** timeStamp is an 8-byte array showing the time the transaction is signed by the kernel, in nanoseconds. */
    private final byte[] timeStamp;

    /** the elliptic curve signature
     * (including public key recovery bits) */
    private final ISignature signature;

    /** transactionHashWithoutSignature is used to create and later verify the signature */
    private final byte[] transactionHashWithoutSignature;

    /** rlpEncoding is saved because it is needed to calculate the transactionHash */
    private final byte[] rlpEncoding;

    /** Main constructor for AionTransaction */
    AionTransaction(
            byte[] nonce,
            AionAddress senderAddress,
            AionAddress destinationAddress,
            byte[] value,
            byte[] transactionData,
            long energyLimit,
            long energyPrice,
            byte txType,
            byte[] timeStamp,
            byte[] transactionHashWithoutSignature,
            ISignature signature,
            byte[] rlpEncoding,
            byte[] transactionHash) {

        this.nonce = nonce;
        this.value = value;
        this.type = txType;
        this.timeStamp = timeStamp;
        this.transactionHashWithoutSignature = transactionHashWithoutSignature;
        this.signature = signature;
        this.rlpEncoding = rlpEncoding;

        if (destinationAddress == null) {
            this.transaction =
                Transaction.contractCreateTransaction(
                    senderAddress,
                    transactionHash,
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
                    transactionHash,
                    new BigInteger(1, nonce),
                    new BigInteger(1, value),
                    transactionData,
                    energyLimit,
                    energyPrice);
        }
    }

    public byte[] getTransactionHash() {
        return transaction.copyOfTransactionHash();
    }

    public byte[] getTransactionHashWithoutSignature() {
        return transactionHashWithoutSignature;
    }

    public byte[] getEncoded() {
        return rlpEncoding;
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

    @Override
    public String toString() {
        return "TransactionData ["
                + transaction.toString()
                + ", txType="
                + type
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", signature="
                + ((signature == null) ? "null" : signature.toString())
                + "]";
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getTransactionHash());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof AionTransaction)) {
            return false;
        } else {
            AionTransaction otherObject = (AionTransaction)obj;
            return this.transaction.equals(otherObject.transaction)
                && Arrays.equals(this.getValue(), otherObject.getValue())
                && Arrays.equals(this.getNonce(), otherObject.getNonce())
                && this.getType() == otherObject.getType()
                && Arrays.equals(this.getTimestamp(), otherObject.getTimestamp())
                && Objects.equals(this.getSignature(), otherObject.getSignature())
                && Arrays.equals(this.getTransactionHashWithoutSignature(), otherObject.getTransactionHashWithoutSignature())
                && Arrays.equals(this.getEncoded(), otherObject.getEncoded());
        }
    }
}
