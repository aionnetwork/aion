package org.aion.zero.types;

import static org.aion.util.bytes.ByteUtil.ZERO_BYTE_ARRAY;
import static org.aion.util.conversions.Hex.toHexString;
import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;

/** aion internal transaction class. */
public class AionInternalTx {

    private boolean rejected = false;
    protected byte[] hash;
    protected byte[] value;
    protected byte[] data;
    protected AionAddress destination;
    protected AionAddress sender;
    protected byte[] nonce;
    protected long nrg;
    protected long nrgPrice;

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
            byte[] data) {

        this.nonce = nonce;
        this.sender = sendAddress;
        this.destination = receiveAddress;
        this.value = nullToEmpty(value);
        this.data = nullToEmpty(data);
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

    @Override
    public String toString() {
        String type = (destination == null) ? "CREATE" : "CALL";
        String destinationString = (destination == null) ? "" : ", destination = " + destination;

        return "InternalTransaction { "
                + type
                + ", sender = "
                + sender
                + destinationString
                + ", nonce = "
                + getNonceBI()
                + ", value = "
                + new BigInteger(1, value)
                + ", data = "
                + ByteUtil.toHexString(data)
                + ", energy limit = "
                + getEnergyLimit()
                + ", energy price = "
                + getEnergyPrice()
                + ", rejected = "
                + rejected
                + " }";
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

    @Override
    public int hashCode() {
        return sender.hashCode()
                + ((destination == null) ? 0 : destination.hashCode())
                + getNonceBI().intValue() * 17
                + new BigInteger(1, getValue()).intValue() * 71
                + Arrays.hashCode(data)
                + (int) nrg * 127
                + (int) nrgPrice * 5
                + (rejected ? 1 : 0)
                + (destination == null ? 1 : 0);
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
