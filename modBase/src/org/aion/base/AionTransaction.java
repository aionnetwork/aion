package org.aion.base;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKey.MissingPrivateKeyException;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.time.TimeInstant;
import org.aion.util.types.AddressUtils;
import org.slf4j.Logger;
import org.aion.fastvm.FvmConstants;

/** Aion transaction class. */
public class AionTransaction implements Cloneable {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    /* SHA3 hash of the RLP encoded transaction */
    protected byte[] hash;

    /* the amount of ether to transfer (calculated as wei) */
    protected byte[] value;

    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    protected byte[] data;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    protected AionAddress to;

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

    public static final int RLP_TX_NONCE = 0,
            RLP_TX_TO = 1,
            RLP_TX_VALUE = 2,
            RLP_TX_DATA = 3,
            RLP_TX_TIMESTAMP = 4,
            RLP_TX_NRG = 5,
            RLP_TX_NRGPRICE = 6,
            RLP_TX_TYPE = 7,
            RLP_TX_SIG = 8;

    public static byte CALL_KIND = 0;
    public static byte CREATE_KIND = 3;

    /* Tx in encoded form */
    protected byte[] rlpEncoded;

    private byte[] rlpRaw;

    protected AionAddress from;

    /** These four members doesn't include into the RLP encode data */
    private long txIndexInBlock = 0;

    private long blockNumber = 0;
    private byte[] blockHash = null;
    private long nrgConsume = 0;

    /*
     * Indicates if this transaction has been parsed from the RLP-encoded data
     */
    protected boolean parsed = false;

    public AionTransaction(byte[] encodedData) {
        this.rlpEncoded = encodedData;
        parsed = false;
    }

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
        parsed = true;
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
        parsed = true;
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
        parsed = true;
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
        parsed = true;
    }

    // For InternalTx constructor
    public AionTransaction(byte[] nonce, AionAddress to, byte[] value, byte[] data) {
        this(nonce, to, value, data, 0L, 0L);
        parsed = true;
    }

    @Override
    public AionTransaction clone() {
        if (!parsed) {
            rlpParse();
        }

        AionTransaction tx2 = new AionTransaction(nonce, to, value, data, nrg, nrgPrice, type);

        tx2.signature = signature; // NOTE: reference copy
        tx2.timeStamp = timeStamp;

        return tx2;
    }

    public void rlpParse() {

        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList tx = (RLPList) decodedTxList.get(0);

        this.nonce = tx.get(RLP_TX_NONCE).getRLPData();
        this.value = tx.get(RLP_TX_VALUE).getRLPData();
        this.data = tx.get(RLP_TX_DATA).getRLPData();

        byte[] rlpTo = tx.get(RLP_TX_TO).getRLPData();
        if (rlpTo == null || rlpTo.length == 0) {
            this.to = null;
        } else {
            this.to = new AionAddress(tx.get(RLP_TX_TO).getRLPData());
        }

        this.timeStamp = tx.get(RLP_TX_TIMESTAMP).getRLPData();
        this.nrg = new BigInteger(1, tx.get(RLP_TX_NRG).getRLPData()).longValue();
        this.nrgPrice = new BigInteger(1, tx.get(RLP_TX_NRGPRICE).getRLPData()).longValue();
        this.type = new BigInteger(1, tx.get(RLP_TX_TYPE).getRLPData()).byteValue();

        byte[] sigs = tx.get(RLP_TX_SIG).getRLPData();
        if (sigs != null) {
            // Singature Factory will decode the signature based on the algo
            // presetted in main() entry.
            ISignature is = SignatureFac.fromBytes(sigs);
            if (is != null) {
                this.signature = is;
            } else {
                // still ok if signature is null, but log it out.
                this.signature = null;
                LOG.error("tx -> unable to decode signature");
            }
        } else {
            LOG.error("tx -> no signature found");
        }

        this.parsed = true;
        this.hash = this.getTransactionHash();
    }

    public byte[] getTransactionHash() {
        if (hash != null) {
            return hash;
        }

        if (!parsed) {
            rlpParse();
        }
        byte[] plainMsg = this.getEncoded();
        // cache it.
        hash = HashUtil.h256(plainMsg);
        return hash;
    }

    public byte[] getRawHash() {
        if (!parsed) {
            rlpParse();
        }
        byte[] plainMsg = this.getEncodedRaw();
        return HashUtil.h256(plainMsg);
    }

    public byte[] getNonce() {
        if (!parsed) {
            rlpParse();
        }
        return nonce == null ? ByteUtil.ZERO_BYTE_ARRAY : nonce;
    }

    public BigInteger getNonceBI() {
        return new BigInteger(1, getNonce());
    }

    public byte[] getTimestamp() {
        return getTimeStamp();
    }

    public byte[] getTimeStamp() {
        if (!parsed) {
            rlpParse();
        }
        return this.timeStamp == null ? ByteUtil.ZERO_BYTE_ARRAY : this.timeStamp;
    }

    public BigInteger getTimeStampBI() {
        return new BigInteger(1, getTimestamp());
    }

    public long getEnergyLimit() {
        if (!parsed) {
            rlpParse();
        }
        return this.nrg;
    }

    public long getEnergyPrice() {
        if (!parsed) {
            rlpParse();
        }
        return this.nrgPrice;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = BigInteger.valueOf(timeStamp).toByteArray();
        this.parsed = true;
    }

    public byte[] getValue() {
        if (!parsed) {
            rlpParse();
        }
        return value == null ? ByteUtil.ZERO_BYTE_ARRAY : value;
    }

    public AionAddress getDestinationAddress() {
        if (!parsed) {
            rlpParse();
        }
        return to;
    }

    public byte[] getData() {
        if (!parsed) {
            rlpParse();
        }
        return data;
    }

    public byte getTargetVM() {
        if (!parsed) {
            rlpParse();
        }
        return this.type;
    }

    public ISignature getSignature() {
        if (!parsed) {
            rlpParse();
        }
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
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public boolean isContractCreationTransaction() {
        if (!parsed) {
            rlpParse();
        }

        return this.to == null;
    }

    public synchronized AionAddress getSenderAddress() {
        if (from != null) {
            return this.from;
        }

        if (!parsed) {
            rlpParse();
        }

        if (this.signature == null) {
            LOG.error("no signature!");
            return null;
        }

        try {
            from = new AionAddress(this.signature.getAddress());
            return from;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public void sign(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochMicro());
        this.signature = key.sign(this.getRawHash());
        this.rlpEncoded = null;
    }

    public void signWithSecTimeStamp(ECKey key) throws MissingPrivateKeyException {
        this.timeStamp = ByteUtil.longToBytes(TimeInstant.now().toEpochSec() * 1_000_000L);
        this.signature = key.sign(this.getRawHash());
        this.rlpEncoded = null;
    }

    @Override
    public String toString() {
        return toString(Integer.MAX_VALUE);
    }

    public String toString(int maxDataSize) {
        if (!parsed) {

            rlpParse();
        }
        String dataS;
        if (data == null) {
            dataS = "";
        } else if (data.length < maxDataSize) {
            dataS = ByteUtil.toHexString(data);
        } else {
            dataS =
                    ByteUtil.toHexString(Arrays.copyOfRange(data, 0, maxDataSize))
                            + "... ("
                            + data.length
                            + " bytes)";
        }
        return "TransactionData ["
                + "hash="
                + ByteUtil.toHexString(hash)
                + ", nonce="
                + new BigInteger(1, nonce)
                + ", receiveAddress="
                + (to == null ? "" : to.toString())
                + ", value="
                + new BigInteger(1, value)
                + ", data="
                + dataS
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

    /** For signatures you have to keep also RLP of the transaction without any signature data */
    public byte[] getEncodedRaw() {

        if (!parsed) {
            rlpParse();
        }
        if (rlpRaw != null) {
            return rlpRaw;
        }

        byte[] nonce = RLP.encodeElement(this.nonce);

        byte[] to;
        if (this.to == null) {
            to = RLP.encodeElement(null);
        } else {
            to = RLP.encodeElement(this.to.toByteArray());
        }

        byte[] value = RLP.encodeElement(this.value);
        byte[] data = RLP.encodeElement(this.data);
        byte[] timeStamp = RLP.encodeElement(this.timeStamp);
        byte[] nrg = RLP.encodeLong(this.nrg);
        byte[] nrgPrice = RLP.encodeLong(this.nrgPrice);
        byte[] type = RLP.encodeByte(this.type);

        rlpRaw = RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type);
        return rlpRaw;
    }

    public byte[] getEncoded() {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        byte[] nonce = RLP.encodeElement(this.nonce);

        byte[] to;
        if (this.to == null) {
            to = RLP.encodeElement(null);
        } else {
            to = RLP.encodeElement(this.to.toByteArray());
        }

        byte[] value = RLP.encodeElement(this.value);
        byte[] data = RLP.encodeElement(this.data);
        byte[] timeStamp = RLP.encodeElement(this.timeStamp);
        byte[] nrg = RLP.encodeLong(this.nrg);
        byte[] nrgPrice = RLP.encodeLong(this.nrgPrice);
        byte[] type = RLP.encodeByte(this.type);

        byte[] sigs;

        if (signature == null) {
            LOG.error("Encoded transaction has no signature!");
            return null;
        }

        sigs = RLP.encodeElement(signature.toBytes());
        this.rlpEncoded =
                RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type, sigs);
        this.hash = this.getTransactionHash();

        return rlpEncoded;
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

    public static AionTransaction create(
            String to, BigInteger amount, BigInteger nonce, long nrg, long nrgPrice) {
        return new AionTransaction(
                nonce.toByteArray(),
                AddressUtils.wrapAddress(to),
                amount.toByteArray(),
                null,
                nrg,
                nrgPrice);
    }

    public void setEncoded(byte[] _encodedData) {
        this.rlpEncoded = _encodedData;
        parsed = false;
    }

    public BigInteger nrgPrice() {
        return BigInteger.valueOf(nrgPrice);
    }

    public BigInteger nrgLimit() {
        return BigInteger.valueOf(nrg);
    }

    public long getTransactionCost() {
        return transactionCost(0);
    }

    public byte getKind() {
        return this.isContractCreationTransaction() ? CREATE_KIND : CALL_KIND;
    }

    public long transactionCost(long blockNumber) {
        long nonZeroes = nonZeroBytesInData();
        long zeroes = zeroBytesInData();

        return (this.isContractCreationTransaction() ? FvmConstants.CREATE_TRANSACTION_FEE : 0)
                + FvmConstants.TRANSACTION_BASE_FEE
                + zeroes * FvmConstants.ZERO_BYTE_FEE
                + nonZeroes * FvmConstants.NONZERO_BYTE_FEE;
    }

    public long nonZeroBytesInData() {
        int total = (data == null) ? 0 : data.length;

        return total - zeroBytesInData();
    }

    public long zeroBytesInData() {
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
