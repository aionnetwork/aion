package org.aion.zero.types;

import static org.aion.util.conversions.Hex.toHexString;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.aion.base.AionTransaction;
import org.aion.crypto.ECKey;
import org.aion.mcf.types.InternalTransactionInterface;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

/** aion internal transaction class. */
public class AionInternalTx extends AionTransaction implements InternalTransactionInterface {

    private byte[] parentHash;
    private int deep;
    private int index;
    private boolean rejected = false;
    private String note;

    public AionInternalTx(byte[] rawData) {
        super(rawData);
    }

    public AionInternalTx(
            byte[] parentHash,
            int deep,
            int index,
            byte[] nonce,
            AionAddress sendAddress,
            AionAddress receiveAddress,
            byte[] value,
            byte[] data,
            String note) {

        // @TODO: pass null to nrg and nrgprice for base class ( Transaction )
        // will be safe?
        super(nonce, receiveAddress, nullToEmpty(value), nullToEmpty(data));

        this.parentHash = parentHash;
        this.deep = deep;
        this.index = index;
        this.from = sendAddress;
        this.note = note;
        this.parsed = true;
    }

    // @TODO: check this functions used by whom
    private static byte[] getData(DataWordImpl nrgPrice) {
        return (nrgPrice == null) ? ByteUtil.EMPTY_BYTE_ARRAY : nrgPrice.getData();
    }

    @Override
    public void markAsRejected() {
        this.rejected = true;
    }

    public int getStackDepth() {
        if (!parsed) {
            rlpParse();
        }
        return deep;
    }

    public int getIndexOfInternalTransaction() {
        if (!parsed) {
            rlpParse();
        }
        return index;
    }

    public boolean isRejected() {
        if (!parsed) {
            rlpParse();
        }
        return rejected;
    }

    public String getNote() {
        if (!parsed) {
            rlpParse();
        }
        return note;
    }

    @Override
    public AionAddress getSenderAddress() {
        if (!parsed) {
            rlpParse();
        }
        return from;
    }

    public byte[] getParentTransactionHash() {
        if (!parsed) {
            rlpParse();
        }
        return parentHash;
    }

    @Override
    public byte[] getEncoded() {
        if (rlpEncoded == null) {

            byte[] to =
                    (this.getDestinationAddress() == null)
                            ? new byte[0]
                            : this.getDestinationAddress().toByteArray();
            byte[] nonce = getNonce();
            boolean isEmptyNonce = isEmpty(nonce) || (getLength(nonce) == 1 && nonce[0] == 0);

            this.rlpEncoded =
                    RLP.encodeList(
                            RLP.encodeElement(isEmptyNonce ? null : nonce),
                            RLP.encodeElement(this.parentHash),
                            RLP.encodeElement(this.getSenderAddress().toByteArray()),
                            RLP.encodeElement(to),
                            RLP.encodeElement(getValue()),
                            RLP.encodeElement(getData()),
                            RLP.encodeString(this.note),
                            encodeInt(this.deep),
                            encodeInt(this.index),
                            encodeInt(this.rejected ? 1 : 0));
        }

        return rlpEncoded;
    }

    @Override
    public byte[] getEncodedRaw() {
        return getEncoded();
    }

    @Override
    public void rlpParse() {
        RLPList decodedTxList = RLP.decode2(rlpEncoded);
        RLPList transaction = (RLPList) decodedTxList.get(0);

        int rlpIdx = 0;
        this.nonce = transaction.get(rlpIdx++).getRLPData();
        this.parentHash = transaction.get(rlpIdx++).getRLPData();
        this.from = new AionAddress(transaction.get(rlpIdx++).getRLPData());
        this.to = new AionAddress(transaction.get(rlpIdx++).getRLPData());
        this.value = transaction.get(rlpIdx++).getRLPData();

        // TODO: check the order
        this.data = transaction.get(rlpIdx++).getRLPData();
        this.note = new String(transaction.get(rlpIdx++).getRLPData());
        this.deep = decodeInt(transaction.get(rlpIdx++).getRLPData());
        this.index = decodeInt(transaction.get(rlpIdx++).getRLPData());
        this.rejected = decodeInt(transaction.get(rlpIdx++).getRLPData()) == 1;

        this.parsed = true;
    }

    private static byte[] encodeInt(int value) {
        return RLP.encodeElement(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static int decodeInt(byte[] encoded) {
        return isEmpty(encoded) ? 0 : new BigInteger(encoded).intValue();
    }

    @Override
    public void sign(ECKey key) throws ECKey.MissingPrivateKeyException {
        throw new UnsupportedOperationException("Cannot sign internal transaction.");
    }

    @Override
    public String toString() {
        String to =
                (this.getDestinationAddress() == null)
                        ? ""
                        : this.getDestinationAddress().toString();
        return "TransactionData ["
                + "  parentHash="
                + toHexString(getParentTransactionHash())
                + ", hash="
                + toHexString(this.getTransactionHash())
                + ", nonce="
                + toHexString(getNonce())
                + ", fromAddress="
                + this.getSenderAddress().toString()
                + ", toAddress="
                + to
                + ", value="
                + toHexString(getValue())
                + ", data="
                + toHexString(getData())
                + ", note="
                + getNote()
                + ", deep="
                + getStackDepth()
                + ", index="
                + getIndexOfInternalTransaction()
                + ", rejected="
                + isRejected()
                + "]";
    }
}
