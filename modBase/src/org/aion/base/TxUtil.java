package org.aion.base;

import java.math.BigInteger;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.fastvm.FvmConstants;
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

        byte[] nonce = RLP.encodeElement(tx.getNonce());
        byte[] to = RLP.encodeElement(tx.getDestinationAddress() == null ? null : tx.getDestinationAddress().toByteArray());
        byte[] value = RLP.encodeElement(tx.getValue());
        byte[] data = RLP.encodeElement(tx.getData());
        byte[] timeStamp = RLP.encodeElement(tx.getTimestamp());
        byte[] nrg = RLP.encodeLong(tx.getEnergyLimit());
        byte[] nrgPrice = RLP.encodeLong(tx.getEnergyPrice());
        byte[] type = RLP.encodeByte(tx.getTargetVM());

        if (withSignature) {
            if (tx.getSignature() == null) {
                throw new IllegalArgumentException();
            }
            byte[] sigs =
                    RLP.encodeElement(
                            tx.getSignature() == null ? null : tx.getSignature().toBytes());
            return RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type, sigs);
        } else {
            return RLP.encodeList(nonce, to, value, data, timeStamp, nrg, nrgPrice, type);
        }
    }

    public static long calculateTransactionCost(AionTransaction tx) {
        long zeroes = zeroBytesInData(tx.getData());
        long nonZeroes = tx.getData().length - zeroes;

        return (tx.isContractCreationTransaction() ? FvmConstants.CREATE_TRANSACTION_FEE : 0)
                + FvmConstants.TRANSACTION_BASE_FEE
                + zeroes * FvmConstants.ZERO_BYTE_FEE
                + nonZeroes * FvmConstants.NONZERO_BYTE_FEE;
    }

    private static long zeroBytesInData(byte[] data) {
        if (data == null) {
            return 0;
        }

        int c = 0;
        for (byte b : data) {
            c += (b == 0) ? 1 : 0;
        }
        return c;
    }

    public static AionAddress calculateContractAddress(AionTransaction tx) {
        if (tx.getDestinationAddress() != null) {
            return null;
        }
        return new AionAddress(
            HashUtil.calcNewAddr(tx.getSenderAddress().toByteArray(), tx.getNonce()));
    }

    public static byte[] hashWithoutSignature(AionTransaction tx) {
        byte[] plainMsg = encodeWithoutSignature(tx);
        return HashUtil.h256(plainMsg);
    }
}
