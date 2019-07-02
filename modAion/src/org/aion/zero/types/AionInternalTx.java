package org.aion.zero.types;

import static org.aion.util.bytes.ByteUtil.ZERO_BYTE_ARRAY;
import static org.aion.util.conversions.Hex.toHexString;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.crypto.HashUtil;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.rlp.RLP;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

/** aion internal transaction class. */
public class AionInternalTx {

    /* Tx in encoded form */
    private byte[] rlpEncoded;

    private boolean rejected = false;
    private String note;

    protected byte[] hash;
    protected byte[] value;
    protected byte[] data;
    protected AionAddress destination;
    protected AionAddress sender;
    protected byte[] nonce;
    protected long nrg;
    protected long nrgPrice;
    protected byte type = TransactionTypes.DEFAULT;

    public AionInternalTx(
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice) {
        this.nonce = nonce;
        this.destination = destination;
        this.value = nullToEmpty(value);
        this.data = nullToEmpty(data);
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
    }

    public AionInternalTx(
            byte[] nonce,
            AionAddress sendAddress,
            AionAddress receiveAddress,
            byte[] value,
            byte[] data,
            String note) {

        this.nonce = nonce;
        this.sender = sendAddress;
        this.destination = receiveAddress;
        this.value = nullToEmpty(value);
        this.data = nullToEmpty(data);
        this.note = note;
    }

    public void markAsRejected() {
        this.rejected = true;
    }

    public boolean isRejected() {
        return rejected;
    }

    public AionAddress getSenderAddress() {
        return sender;
    }

    private static byte[] encodeInt(int value) {
        return RLP.encodeElement(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    @Override
    public String toString() {
        String to = (getDestinationAddress() == null) ? "" : getDestinationAddress().toString();
        return "TransactionData ["
                + ", hash="
                + toHexString(getTransactionHash())
                + ", nonce="
                + toHexString(getNonce())
                + ", fromAddress="
                + getSenderAddress().toString()
                + ", toAddress="
                + to
                + ", value="
                + toHexString(getValue())
                + ", data="
                + toHexString(getData())
                + ", rejected="
                + isRejected()
                + "]";
    }

    public byte[] getTransactionHash() {
        if (hash != null) {
            return hash;
        }

        byte[] plainMsg = this.getEncoded();
        // cache it.
        hash = HashUtil.h256(plainMsg);
        return hash;
    }

    public byte[] getNonce() {
        return nonce == null ? ZERO_BYTE_ARRAY : nonce;
    }

    public BigInteger getNonceBI() {
        return new BigInteger(1, getNonce());
    }

    public long getEnergyLimit() {
        return nrg;
    }

    public long getEnergyPrice() {
        return nrgPrice;
    }

    public byte[] getValue() {
        return value == null ? ZERO_BYTE_ARRAY : value;
    }

    public AionAddress getDestinationAddress() {
        return destination;
    }

    public byte[] getData() {
        return data;
    }

    public byte getTargetVM() {
        return type;
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
        return destination == null;
    }

    public String toString(int maxDataSize) {
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
                + (destination == null ? "" : destination.toString())
                + ", value="
                + new BigInteger(1, value)
                + ", data="
                + dataS
                + ", Nrg="
                + this.nrg
                + ", NrgPrice="
                + this.nrgPrice
                + ", txType="
                + this.type
                + "]";
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {

            byte[] to =
                    (this.getDestinationAddress() == null)
                            ? new byte[0]
                            : this.getDestinationAddress().toByteArray();
            byte[] nonce = getNonce();
            boolean isEmptyNonce = isEmpty(nonce) || (getLength(nonce) == 1 && nonce[0] == 0);

            rlpEncoded =
                    RLP.encodeList(
                            RLP.encodeElement(isEmptyNonce ? null : nonce),
                            RLP.encodeElement(this.getSenderAddress().toByteArray()),
                            RLP.encodeElement(to),
                            RLP.encodeElement(getValue()),
                            RLP.encodeElement(getData()),
                            RLP.encodeString(this.note),
                            encodeInt(this.rejected ? 1 : 0));
        }

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
        if (!(obj instanceof AionInternalTx)) {
            return false;
        }
        AionInternalTx tx = (AionInternalTx) obj;

        return tx.hashCode() == this.hashCode();
    }
}
