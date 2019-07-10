package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKey.MissingPrivateKeyException;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;

/** Aion transaction class. */
public class AionTransaction implements Cloneable {

    protected final AionAddress senderAddress;
    protected final AionAddress destinationAddress;
    // TODO transactionHash
    protected final byte[] value;
    protected final byte[] nonce;
    protected final long energyPrice;
    protected final long energyLimit;
    // TODO isCreate
    protected final byte[] transactionData;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    protected byte[] timeStamp;

    /* define transaction type. */
    protected byte type;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    protected ISignature signature;

    private byte[] transactionHash;

    private long nrgConsume = 0;

    public AionTransaction(
            byte[] nonce,
            AionAddress senderAddress,
            AionAddress destinationAddress,
            byte[] value,
            byte[] transactionData,
            long energyLimit,
            long energyPrice) {

        if (senderAddress == null) {
            throw new IllegalArgumentException();
        }
        if (nonce == null) {
            throw new IllegalArgumentException();
        }

        this.nonce = nonce;
        this.destinationAddress = destinationAddress;
        this.value = value;
        this.transactionData = transactionData;
        this.type = TransactionTypes.DEFAULT;
        this.energyLimit = energyLimit;
        this.energyPrice = energyPrice;
        this.senderAddress = senderAddress;
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
            byte txType) {

        this(
                nonce,
                senderAddress,
                destinationAddress,
                value,
                transactionData,
                energyLimit,
                energyPrice);
        this.type = txType;
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

        this(
                nonce,
                senderAddress,
                destinationAddress,
                value,
                transactionData,
                energyLimit,
                energyPrice,
                txType);
        this.signature = signature;
        this.timeStamp = timeStamp;
    }

    @Override
    public AionTransaction clone() {
        return new AionTransaction(
                nonce,
                senderAddress,
                destinationAddress,
                value,
                transactionData,
                energyLimit,
                energyPrice,
                type,
                signature,
                timeStamp);
    }

    public byte[] getTransactionHash() {
        if (transactionHash == null) {
            transactionHash = HashUtil.h256(getEncoded());
        }
        return transactionHash;
    }

    public byte[] getEncoded() {
        return TxUtil.encode(this);
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getValue() {
        return value;
    }

    public BigInteger getNonceBI() {
        return new BigInteger(1, nonce);
    }

    public BigInteger getValueBI() {
        return new BigInteger(1, value);
    }

    public byte[] getTimestamp() {
        return this.timeStamp == null ? ByteUtil.ZERO_BYTE_ARRAY : this.timeStamp;
    }

    public BigInteger getTimeStampBI() {
        return new BigInteger(1, getTimestamp());
    }

    public long getEnergyLimit() {
        return this.energyLimit;
    }

    public long getEnergyPrice() {
        return this.energyPrice;
    }

    public AionAddress getDestinationAddress() {
        return destinationAddress;
    }

    public byte[] getData() {
        return transactionData;
    }

    public byte getTargetVM() {
        return this.type;
    }

    public ISignature getSignature() {
        return signature;
    }

    public boolean isContractCreationTransaction() {
        return this.destinationAddress == null;
    }

    public AionAddress getSenderAddress() {
        return senderAddress;
    }

    public void sign(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(TxUtil.hashWithoutSignature(this));
    }

    public void signWithSecTimeStamp(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochSec() * 1_000_000L);
        this.signature = key.sign(TxUtil.hashWithoutSignature(this));
    }

    @Override
    public String toString() {
        return "TransactionData ["
                + "hash="
                + ByteUtil.toHexString(getTransactionHash())
                + ", nonce="
                + new BigInteger(1, nonce)
                + ", receiveAddress="
                + (destinationAddress == null ? "" : destinationAddress.toString())
                + ", value="
                + new BigInteger(1, value)
                + ", data="
                + ByteUtil.toHexString(transactionData)
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", Nrg="
                + this.energyLimit
                + ", NrgPrice="
                + this.energyPrice
                + ", txType="
                + this.type
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

    public BigInteger nrgPrice() {
        return BigInteger.valueOf(energyPrice);
    }

    public BigInteger nrgLimit() {
        return BigInteger.valueOf(energyLimit);
    }

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }
}
