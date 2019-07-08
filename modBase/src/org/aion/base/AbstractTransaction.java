package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ISignature;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.types.AionAddress;
import org.slf4j.Logger;

public abstract class AbstractTransaction implements Transaction {

    private static final int nrgDigits = 64;

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

    public AbstractTransaction() {}

    public AbstractTransaction(
            byte[] nonce, AionAddress receiveAddress, byte[] value, byte[] data) {
        this.nonce = nonce;
        this.to = receiveAddress;
        this.value = value;
        this.data = data;
        this.type = TransactionTypes.DEFAULT;
    }

    public AbstractTransaction(
            byte[] nonce,
            AionAddress receiveAddress,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice) {
        this(nonce, receiveAddress, value, data);
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
    }

    public AbstractTransaction(
            byte[] nonce,
            AionAddress receiveAddress,
            byte[] value,
            byte[] data,
            long nrg,
            long nrgPrice,
            byte type)
            throws Exception {
        this(nonce, receiveAddress, value, data, nrg, nrgPrice);
        this.type = type;
    }

    public abstract byte[] getEncoded();

    public abstract AionAddress getSenderAddress();

    public abstract AionAddress getDestinationAddress();

    public abstract byte[] getNonce();

    public abstract byte[] getTimeStamp();

    public abstract AionAddress getContractAddress();

    public abstract AbstractTransaction clone();

    public abstract long getNrgConsume();

    public abstract void setNrgConsume(long consume);

    public abstract byte getTargetVM();

    public abstract BigInteger getNonceBI();

    public abstract BigInteger getTimeStampBI();
}
