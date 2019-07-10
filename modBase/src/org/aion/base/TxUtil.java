package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.slf4j.Logger;

public final class TxUtil {

    private TxUtil() {
        throw new AssertionError("TxUtil may not be instantiated");
    }

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private static final int RLP_TX_NONCE = 0,
            RLP_TX_TO = 1,
            RLP_TX_VALUE = 2,
            RLP_TX_DATA = 3,
            RLP_TX_TIMESTAMP = 4,
            RLP_TX_NRG = 5,
            RLP_TX_NRGPRICE = 6,
            RLP_TX_TYPE = 7,
            RLP_TX_SIG = 8;

    public static AionTransaction decode(byte[] rlpEncoding) {

        RLPList decodedTxList;
        try {
            decodedTxList = RLP.decode2(rlpEncoding);
        } catch (Exception e) {
            LOG.error("tx -> unable to decode rlpEncoding");
            return null;
        }
        RLPList tx = (RLPList) decodedTxList.get(0);

        byte[] nonce = tx.get(RLP_TX_NONCE).getRLPData();
        byte[] value = tx.get(RLP_TX_VALUE).getRLPData();
        byte[] data = tx.get(RLP_TX_DATA).getRLPData();

        byte[] rlpTo = tx.get(RLP_TX_TO).getRLPData();
        AionAddress destination;
        if (rlpTo == null || rlpTo.length == 0) {
            destination = null;
        } else {
            destination = new AionAddress(tx.get(RLP_TX_TO).getRLPData());
        }

        byte[] timeStamp = tx.get(RLP_TX_TIMESTAMP).getRLPData();
        long nrg = new BigInteger(1, tx.get(RLP_TX_NRG).getRLPData()).longValue();
        long nrgPrice = new BigInteger(1, tx.get(RLP_TX_NRGPRICE).getRLPData()).longValue();
        byte type = new BigInteger(1, tx.get(RLP_TX_TYPE).getRLPData()).byteValue();

        byte[] sigs = tx.get(RLP_TX_SIG).getRLPData();
        ISignature signature;
        AionAddress sender;
        if (sigs != null) {
            // Singature Factory will decode the signature based on the algo
            // presetted in main() entry.
            ISignature is = SignatureFac.fromBytes(sigs);
            if (is != null) {
                signature = is;
                sender = new AionAddress(is.getAddress());
            } else {
                LOG.error("tx -> unable to decode signature");
                return null;
            }
        } else {
            LOG.error("tx -> no signature found");
            return null;
        }

        try {
            return new AionTransaction(
                nonce,
                sender,
                destination,
                value,
                data,
                nrg,
                nrgPrice,
                type,
                signature,
                timeStamp);
        }
        catch (Exception e) {
            LOG.error("tx -> invalid parameter decoded in rlpEncoding");
            return null;
        }
    }

    /** For signatures you have to keep also RLP of the transaction without any signature data */
    static byte[] encodeWithoutSignature(AionTransaction tx) {
        return calculateEncodingPrivate(tx, false);
    }

    static byte[] encode(AionTransaction tx) {
        return calculateEncodingPrivate(tx, true);
    }

    private static byte[] calculateEncodingPrivate(AionTransaction tx, boolean withSignature) {

        byte[] nonce = RLP.encodeElement(tx.nonce);
        byte[] to = RLP.encodeElement(tx.to == null ? null : tx.to.toByteArray());
        byte[] value = RLP.encodeElement(tx.value);
        byte[] data = RLP.encodeElement(tx.data);
        byte[] timeStamp = RLP.encodeElement(tx.timeStamp);
        byte[] nrg = RLP.encodeLong(tx.nrg);
        byte[] nrgPrice = RLP.encodeLong(tx.nrgPrice);
        byte[] type = RLP.encodeByte(tx.type);

        if (withSignature) {
            byte[] sigs = RLP.encodeElement(tx.signature == null ? null : tx.signature.toBytes());
            return RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type, sigs);
        } else {
            return RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type);
        }
    }
}
