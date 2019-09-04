package org.aion.base;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.fastvm.FvmConstants;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Transaction;
import org.slf4j.Logger;

public final class TxUtil {

    private TxUtil() {
        throw new AssertionError("TxUtil may not be instantiated");
    }

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

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

    public static AionAddress calculateContractAddress(Transaction tx) {
        if (tx.destinationAddress != null) {
            return null;
        }
        return calcNewAddr(tx.senderAddress.toByteArray(), tx.nonce);
    }

    public static AionAddress calculateContractAddress(AionTransaction tx) {
        if (tx.getDestinationAddress() != null) {
            return null;
        }
        return calcNewAddr(tx.getSenderAddress().toByteArray(), tx.getNonceBI());
    }

    public static AionAddress calculateContractAddress(InternalTransaction itx) {
        if (itx.destination != null) {
            return null;
        }
        return calcNewAddr(itx.sender.toByteArray(), itx.senderNonce);
    }

    public static AionAddress calculateContractAddress(byte[] addr, BigInteger nonce) {
        return calcNewAddr(addr, nonce);
    }

    /** Calculates the address as per the QA2 definitions */
    private static AionAddress calcNewAddr(byte[] addr, BigInteger nonce) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(AddressSpecs.A0_IDENTIFIER);

        byte[] encSender = RLP.encodeElement(addr);
        byte[] encNonce = RLP.encodeBigInteger(nonce);

        buf.put(HashUtil.h256(RLP.encodeList(encSender, encNonce)), 1, 31);
        return new AionAddress(buf.array());
    }

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
        long energyLimit = new BigInteger(1, tx.get(RLP_TX_NRG).getRLPData()).longValue();
        long energyPrice = new BigInteger(1, tx.get(RLP_TX_NRGPRICE).getRLPData()).longValue();
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
            return AionTransaction.createFromRlp(
                nonce,
                sender,
                destination,
                value,
                data,
                energyLimit,
                energyPrice,
                type,
                timeStamp,
                signature,
                rlpEncoding);
        }
        catch (Exception e) {
            LOG.error("tx -> invalid parameter decoded in rlpEncoding");
            return null;
        }
    }

    /** For signatures you have to keep also RLP of the transaction without any signature data */
    static byte[] rlpEncodeWithoutSignature(
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            byte[] timeStamp,
            long energyLimit,
            long energyPrice,
            byte type) {

        byte[] nonceEncoded = RLP.encodeElement(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeElement(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] timeStampEncoded = RLP.encodeElement(timeStamp);
        byte[] energyLimitEncoded = RLP.encodeLong(energyLimit);
        byte[] energyPriceEncoded = RLP.encodeLong(energyPrice);
        byte[] typeEncoded = RLP.encodeByte(type);

        return RLP.encodeList(
                nonceEncoded,
                destinationEncoded,
                valueEncoded,
                dataEncoded,
                timeStampEncoded,
                energyLimitEncoded,
                energyPriceEncoded,
                typeEncoded);
    }

    static byte[] rlpEncode(
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            byte[] timeStamp,
            long energyLimit,
            long energyPrice,
            byte type,
            ISignature signature) {

        byte[] nonceEncoded = RLP.encodeElement(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeElement(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] timeStampEncoded = RLP.encodeElement(timeStamp);
        byte[] energyLimitEncoded = RLP.encodeLong(energyLimit);
        byte[] energyPriceEncoded = RLP.encodeLong(energyPrice);
        byte[] typeEncoded = RLP.encodeByte(type);
        byte[] signatureEncoded = RLP.encodeElement(signature.toBytes());

        return RLP.encodeList(
                nonceEncoded,
                destinationEncoded,
                valueEncoded,
                dataEncoded,
                timeStampEncoded,
                energyLimitEncoded,
                energyPriceEncoded,
                typeEncoded,
                signatureEncoded);
    }
}
