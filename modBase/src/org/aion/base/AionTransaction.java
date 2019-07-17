package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.util.bytes.ByteUtil;

/** Aion transaction class. */
public class AionTransaction {

    private final Transaction transaction;
    private final byte[] value;
    private final byte[] nonce;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    private final byte[] timeStamp;

    /* define transaction type. */
    private final byte type;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private final ISignature signature;

    private final byte[] transactionHashWithoutSignature;

    private final byte[] rlpEncoding;

    private long nrgConsume = 0;

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

    @Override
    public String toString() {
        return "TransactionData ["
                + "hash="
                + ByteUtil.toHexString(getTransactionHash())
                + ", nonce="
                + new BigInteger(1, nonce)
                + ", receiveAddress="
                + (getDestinationAddress() == null ? "" : getDestinationAddress().toString())
                + ", value="
                + new BigInteger(1, value)
                + ", data="
                + ByteUtil.toHexString(transaction.copyOfTransactionData())
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", Nrg="
                + transaction.energyLimit
                + ", NrgPrice="
                + transaction.energyPrice
                + ", txType="
                + type
                + ", sig="
                + ((signature == null) ? "null" : signature.toString())
                + "]";
    }

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

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }
}
