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

public class TransactionRlpCodec {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private static final int RLP_TX_NONCE = 0,
            RLP_TX_TO = 1,
            RLP_TX_VALUE = 2,
            RLP_TX_DATA = 3,
            RLP_TX_TIMESTAMP = 4,
            RLP_TX_NRG = 5,
            RLP_TX_NRGPRICE = 6,
            RLP_TX_TYPE = 7,
            RLP_TX_SIG = 8;

    public static AionTransaction decodeTransaction(byte[] rlpEncoding) {

        RLPList decodedTxList = RLP.decode2(rlpEncoding);
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
                throw new IllegalArgumentException("Unable to parse signature data");
            }
        } else {
            throw new IllegalArgumentException("No Signature data");
        }
        // this.hash = this.getTransactionHash();

        return new AionTransaction(
                nonce, sender, destination, value, data, nrg, nrgPrice, type, signature, timeStamp);
    }

    /** For signatures you have to keep also RLP of the transaction without any signature data */
    public static byte[] getEncodingNoSignature(AionTransaction tx) {
        return getEncodingPrivate(tx, false);
    }

    public static byte[] getEncoding(AionTransaction tx) {
        return getEncodingPrivate(tx, true);
    }

    private static byte[] getEncodingPrivate(AionTransaction tx, boolean withSignature) {

        byte[] nonce = RLP.encodeElement(tx.nonce);

        byte[] to;
        if (tx.to == null) {
            to = RLP.encodeElement(null);
        } else {
            to = RLP.encodeElement(tx.to.toByteArray());
        }

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
