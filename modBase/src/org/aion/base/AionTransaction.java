package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKey.MissingPrivateKeyException;
import org.aion.crypto.HashUtil;
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
    protected byte[] timeStamp;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private ISignature signature;

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
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(TransactionUtil.hashWithoutSignature(this));
    }

    // constructor for explicitly setting a transaction type.
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
            byte[] timeStamp) {

        if (senderAddress == null) {
            throw new IllegalArgumentException();
        }
        if (nonce == null) {
            throw new IllegalArgumentException();
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
                timeStamp);
    }

    public byte[] getTransactionHash() {
        return HashUtil.h256(TransactionRlpCodec.encode(this));
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
        return this.timeStamp == null ? ByteUtil.ZERO_BYTE_ARRAY : this.timeStamp;
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

    public void sign(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(TransactionUtil.hashWithoutSignature(this));
    }

    public void signWithSecTimeStamp(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochSec() * 1_000_000L);
        this.signature = key.sign(TransactionUtil.hashWithoutSignature(this));
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
