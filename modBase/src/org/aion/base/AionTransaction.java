package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKey.MissingPrivateKeyException;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;
import org.slf4j.Logger;
import org.aion.fastvm.FvmConstants;

/** Aion transaction class. */
public class AionTransaction implements Cloneable {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

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

    private byte[] transactionHash;

    /** These four members doesn't include into the RLP encode data */
    private long txIndexInBlock = 0;

    private long blockNumber = 0;
    private byte[] blockHash = null;
    private long nrgConsume = 0;

    // constructor for create nrgEstimate transaction
    public AionTransaction(
            byte[] nonce,
            AionAddress from,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice) {

        if (from == null) {
            throw new IllegalArgumentException();
        }

        this.nonce = nonce;
        this.to = destination;
        this.value = value;
        this.data = data;
        this.type = TransactionTypes.DEFAULT;
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
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

        this(nonce, from, to, value, data, nrg, nrgPrice);
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
        AionTransaction tx2 =
                new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice, type);

        tx2.signature = signature; // NOTE: reference copy
        tx2.timeStamp = timeStamp;

        return tx2;
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

    public boolean isContractCreationTransaction() {
        return this.to == null;
    }

    public AionAddress getSenderAddress() {
        return from;
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
