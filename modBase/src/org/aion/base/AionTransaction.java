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

    /* the amount of ether to transfer (calculated as wei) */
    protected byte[] value;

    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    protected byte[] data;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    protected AionAddress to;

    protected AionAddress from;

    /* a counter used to make sure each transaction can only be processed once */
    protected byte[] nonce;

    /* timeStamp is a 8-bytes array shown the time of the transaction signed by the kernel, the unit is nanosecond. */
    protected byte[] timeStamp;

    protected long nrg;

    protected long nrgPrice;

    /* define transaction type. */
    protected byte type;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    protected ISignature signature;

    /** These four members doesn't include into the RLP encode data */
    private long txIndexInBlock = 0;

    private long blockNumber = 0;
    private byte[] blockHash = null;
    private long nrgConsume = 0;

    public AionTransaction(
            byte[] nonce,
            AionAddress receiveAddress,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice) {
        this.nonce = nonce;
        this.to = receiveAddress;
        this.value = value;
        this.data = data;
        this.type = TransactionTypes.DEFAULT;
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
    }

    public AionTransaction(
            byte[] nonce,
            AionAddress to,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice,
            byte type) {
        this(nonce, to, value, data, nrg, nrgPrice);
        this.type = type;
    }

    // constructor for create nrgEstimate transaction
    public AionTransaction(
            byte[] nonce,
            AionAddress from,
            AionAddress to,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice) {
        this(nonce, to, value, data, nrg, nrgPrice);
        this.from = from;
    }

    // constructor for explicitly setting a transaction type.
    public AionTransaction(
            byte[] nonce,
            AionAddress from,
            AionAddress to,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice,
            byte txType) {

        this(nonce, to, value, data, nrg, nrgPrice);
        this.from = from;
        this.type = txType;
    }

    // constructor for explicitly setting a transaction type.
    public AionTransaction(
            byte[] nonce,
            AionAddress from,
            AionAddress to,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice,
            byte txType,
            ISignature signature,
            byte[] timeStamp) {

        this(nonce, from, to, value, data, nrg, nrgPrice, txType);
        this.signature = signature;
        this.timeStamp = timeStamp;
    }

    @Override
    public AionTransaction clone() {
        AionTransaction tx2 = new AionTransaction(nonce, to, value, data, nrg, nrgPrice, type);

        tx2.signature = signature; // NOTE: reference copy
        tx2.timeStamp = timeStamp;

        return tx2;
    }

    public byte[] getTransactionHash() {
        return HashUtil.h256(TransactionRlpCodec.getEncoding(this));
    }

    public byte[] getRawHash() {
        byte[] plainMsg = TransactionRlpCodec.getEncodingNoSignature(this);
        return HashUtil.h256(plainMsg);
    }

    public byte[] getNonce() {
        return nonce == null ? ByteUtil.ZERO_BYTE_ARRAY : nonce;
    }

    public BigInteger getNonceBI() {
        return new BigInteger(1, getNonce());
    }

    public byte[] getTimestamp() {
        return this.timeStamp == null ? ByteUtil.ZERO_BYTE_ARRAY : this.timeStamp;
    }

    public BigInteger getTimeStampBI() {
        return new BigInteger(1, getTimestamp());
    }

    public long getEnergyLimit() {
        return this.nrg;
    }

    public long getEnergyPrice() {
        return this.nrgPrice;
    }

    public byte[] getValue() {
        return value == null ? ByteUtil.ZERO_BYTE_ARRAY : value;
    }

    public AionAddress getDestinationAddress() {
        return to;
    }

    public byte[] getData() {
        return data;
    }

    public byte getTargetVM() {
        return this.type;
    }

    public ISignature getSignature() {
        return signature;
    }

    public AionAddress getContractAddress() {
        if (!this.isContractCreationTransaction()) {
            return null;
        }

        AionAddress from = this.getSenderAddress();

        if (from == null) {
            return null;
        }

        try {
            return new AionAddress(HashUtil.calcNewAddr(from.toByteArray(), this.getNonce()));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isContractCreationTransaction() {
        return this.to == null;
    }

    public synchronized AionAddress getSenderAddress() {
        if (from != null) {
            return this.from;
        }

        if (this.signature == null) {
            return null;
        }

        try {
            from = new AionAddress(this.signature.getAddress());
            return from;
        } catch (Exception e) {
            return null;
        }
    }

    public void sign(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(this.getRawHash());
    }

    public void signWithSecTimeStamp(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochSec() * 1_000_000L);
        this.signature = key.sign(this.getRawHash());
    }

    @Override
    public String toString() {
        return "TransactionData ["
                + "hash="
                + ByteUtil.toHexString(getTransactionHash())
                + ", nonce="
                + new BigInteger(1, nonce)
                + ", receiveAddress="
                + (to == null ? "" : to.toString())
                + ", value="
                + new BigInteger(1, value)
                + ", data="
                + ByteUtil.toHexString(data)
                + ", timeStamp="
                + ByteUtil.byteArrayToLong(timeStamp)
                + ", Nrg="
                + this.nrg
                + ", NrgPrice="
                + this.nrgPrice
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
        return BigInteger.valueOf(nrgPrice);
    }

    public BigInteger nrgLimit() {
        return BigInteger.valueOf(nrg);
    }

    public long getTransactionCost() {
        long nonZeroes = nonZeroBytesInData();
        long zeroes = zeroBytesInData();

        return (this.isContractCreationTransaction() ? Constants.NRG_CREATE_CONTRACT_MIN : 0)
                + Constants.NRG_TRANSACTION_MIN
                + zeroes * Constants.NRG_TX_DATA_ZERO
                + nonZeroes * Constants.NRG_TX_DATA_NONZERO;
    }

    private long nonZeroBytesInData() {
        int total = (data == null) ? 0 : data.length;

        return total - zeroBytesInData();
    }

    private long zeroBytesInData() {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }

    public void setTxIndexInBlock(long idx) {
        this.txIndexInBlock = idx;
    }

    public void setBlockNumber(long blkNr) {
        this.blockNumber = blkNr;
    }

    public void setBlockHash(byte[] hash) {
        this.blockHash = hash;
    }

    public long getTxIndexInBlock() {
        return this.txIndexInBlock;
    }

    public long getBlockNumber() {
        return this.blockNumber;
    }

    public byte[] getBlockHash() {
        return this.blockHash;
    }

    public long getNrgConsume() {
        return this.nrgConsume;
    }

    public void setNrgConsume(long consume) {
        this.nrgConsume = consume;
    }
}
