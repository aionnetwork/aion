package org.aion.base;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;

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

    private long nrgConsume = 0;

    /** Constructor for AionTransaction */
    private AionTransaction(
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

    /** Factory method used by most of the kernel to create AionTransactions */
    public static AionTransaction create(
            ECKey key,
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long energyLimit,
            long energyPrice,
            byte type) {

        byte[] timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());

        byte[] transactionHashWithoutSignature =
                HashUtil.h256(TxUtil.rlpEncodeWithoutSignature(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type));

        ISignature signature = key.sign(transactionHashWithoutSignature);

        byte[] rlpEncoding =
                TxUtil.rlpEncode(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type,
                        signature);

        byte[] transactionHash = HashUtil.h256(rlpEncoding);

        return new AionTransaction(
                nonce,
                new AionAddress(key.getAddress()),
                destination,
                value,
                data,
                energyLimit,
                energyPrice,
                type,
                timeStamp,
                transactionHashWithoutSignature,
                signature,
                rlpEncoding,
                transactionHash);
    }

    /** Factory method used by ApiAion to perform ethCalls */
    public static AionTransaction createWithoutKey(
            byte[] nonce,
            AionAddress sender,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long energyLimit,
            long energyPrice,
            byte type) {

        ECKey key = ECKeyFac.inst().create();

        byte[] timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());

        byte[] transactionHashWithoutSignature =
                HashUtil.h256(TxUtil.rlpEncodeWithoutSignature(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type));

        ISignature signature = key.sign(transactionHashWithoutSignature);

        byte[] rlpEncoding =
                TxUtil.rlpEncode(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type,
                        signature);

        byte[] transactionHash = HashUtil.h256(rlpEncoding);

        return new AionTransaction(
                nonce,
                sender,
                destination,
                value,
                data,
                energyLimit,
                energyPrice,
                type,
                timeStamp,
                transactionHashWithoutSignature,
                signature,
                rlpEncoding,
                transactionHash);
    }

    /** Factory method used by some tests to create multiple transactions with the same timestamp */
    public static AionTransaction newAionTransactionGivenTimestamp(
            ECKey key,
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long energyLimit,
            long energyPrice,
            byte type,
            byte[] timeStamp) {

        byte[] transactionHashWithoutSignature =
                HashUtil.h256(TxUtil.rlpEncodeWithoutSignature(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type));

        ISignature signature = key.sign(transactionHashWithoutSignature);

        byte[] rlpEncoding =
                TxUtil.rlpEncode(
                        nonce,
                        destination,
                        value,
                        data,
                        timeStamp,
                        energyLimit,
                        energyPrice,
                        type,
                        signature);

        byte[] transactionHash = HashUtil.h256(rlpEncoding);

        return new AionTransaction(
                nonce,
                new AionAddress(key.getAddress()),
                destination,
                value,
                data,
                energyLimit,
                energyPrice,
                type,
                timeStamp,
                transactionHashWithoutSignature,
                signature,
                rlpEncoding,
                transactionHash);
    }

    /**
     * Factory method used by TxUtil to create an AionTransaction from an RLP encoding.
     * This is the only way to create a transaction that could fail verifying the
     * hashWithoutSignature against the Signature.
     * */
    static AionTransaction createFromRlp(
            byte[] nonce,
            AionAddress sender,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long energyLimit,
            long energyPrice,
            byte type,
            byte[] timeStamp,
            ISignature signature,
            byte[] rlpEncoding) {

        byte[] transactionHashWithoutSignature =
                HashUtil.h256(
                        TxUtil.rlpEncodeWithoutSignature(
                                nonce,
                                destination,
                                value,
                                data,
                                timeStamp,
                                energyLimit,
                                energyPrice,
                                type));

        byte[] transactionHash = HashUtil.h256(rlpEncoding);

        return new AionTransaction(
                nonce,
                sender,
                destination,
                value,
                data,
                energyLimit,
                energyPrice,
                type,
                timeStamp,
                transactionHashWithoutSignature,
                signature,
                rlpEncoding,
                transactionHash);
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
            return Arrays.equals(this.getTransactionHash(), otherObject.getTransactionHash());
        }
    }

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }
}
