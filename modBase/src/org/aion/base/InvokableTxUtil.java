package org.aion.base;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.InternalTransaction.RejectedStatus;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.slf4j.Logger;

public class InvokableTxUtil {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private static final int
        RLP_META_TX_NONCE = 0,
        RLP_META_TX_TO = 1,
        RLP_META_TX_VALUE = 2,
        RLP_META_TX_DATA = 3,
        RLP_META_TX_EXECUTOR = 4,
        RLP_META_TX_SIG = 5;

    /**
     * Encodes an invokable transaction. The first byte will be the version code.
     * Currently there is only version 0.
     *
     */
    public static byte[] encodeInvokableTransaction(
        ECKey key,
        BigInteger nonce,
        AionAddress destination,
        BigInteger value,
        byte[] data,
        AionAddress executor) {

        byte[] rlpEncodingWithoutSignature =
            rlpEncodeWithoutSignature(
                nonce,
                destination,
                value,
                data,
                executor);

        ISignature signature = key.sign(HashUtil.h256(rlpEncodingWithoutSignature));

        byte[] encoding =
            rlpEncode(
                nonce,
                destination,
                value,
                data,
                executor,
                signature);

        // prepend version code
        return ByteUtil.merge(new byte[] {0}, encoding);
    }

    private static byte[] rlpEncodeWithoutSignature(
        BigInteger nonce,
        AionAddress destination,
        BigInteger value,
        byte[] data,
        AionAddress executor) {

        byte[] nonceEncoded = RLP.encodeBigInteger(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeBigInteger(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] executorEncoded = RLP.encodeElement(executor == null ? null : executor.toByteArray());

        return RLP.encodeList(
            nonceEncoded,
            destinationEncoded,
            valueEncoded,
            dataEncoded,
            executorEncoded);
    }

    private static byte[] rlpEncode(
        BigInteger nonce,
        AionAddress destination,
        BigInteger value,
        byte[] data,
        AionAddress executor,
        ISignature signature) {

        byte[] nonceEncoded = RLP.encodeBigInteger(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeBigInteger(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] executorEncoded = RLP.encodeElement(executor == null ? null : executor.toByteArray());
        byte[] signatureEncoded = RLP.encodeElement(signature.toBytes());

        return RLP.encodeList(
            nonceEncoded,
            destinationEncoded,
            valueEncoded,
            dataEncoded,
            executorEncoded,
            signatureEncoded);
    }


    public static InternalTransaction decode(byte[] encoding, AionAddress callingAddress, long energyPrice, long energyLimit) {
        // Right now we only have version 0, so if it is not 0, return null
        if (encoding[0] != 0) { return null; }

        byte[] rlpEncoding = Arrays.copyOfRange(encoding, 1, encoding.length);

        RLPList decodedTxList;
        try {
            decodedTxList = RLP.decode2(rlpEncoding);
        } catch (Exception e) {
            return null;
        }
        RLPList tx = (RLPList) decodedTxList.get(0);

        BigInteger nonce = new BigInteger(1, tx.get(RLP_META_TX_NONCE).getRLPData());
        BigInteger value = new BigInteger(1, tx.get(RLP_META_TX_VALUE).getRLPData());
        byte[] data = tx.get(RLP_META_TX_DATA).getRLPData();

        AionAddress destination;
        try {
            destination = new AionAddress(tx.get(RLP_META_TX_TO).getRLPData());
        } catch(Exception e) {
            destination = null;
        }

        AionAddress executor;
        try {
            executor = new AionAddress(tx.get(RLP_META_TX_EXECUTOR).getRLPData());
        } catch(Exception e) {
            executor = null;
        }

        // Verify the executor

        if (executor != null && !executor.equals(AddressUtils.ZERO_ADDRESS) && !executor.equals(callingAddress)) {
            return null;
        }

        byte[] sigs = tx.get(RLP_META_TX_SIG).getRLPData();
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
                return null;
            }
        } else {
            return null;
        }

        try {
            return createFromRlp(
                nonce,
                sender,
                destination,
                value,
                data,
                executor,
                energyLimit,
                energyPrice,
                signature,
                rlpEncoding);
        }
        catch (Exception e) {
            LOG.error("Invokable tx -> unable to decode rlpEncoding. " + e);
            return null;
        }
    }

    private static InternalTransaction createFromRlp(
        BigInteger nonce,
        AionAddress sender,
        AionAddress destination,
        BigInteger value,
        byte[] data,
        AionAddress executor,
        long energyLimit,
        long energyPrice,
        ISignature signature,
        byte[] rlpEncoding) {

        byte[] transactionHashWithoutSignature =
            HashUtil.h256(
                InvokableTxUtil.rlpEncodeWithoutSignature(
                    nonce,
                    destination,
                    value,
                    data,
                    executor));

        if (!SignatureFac.verify(transactionHashWithoutSignature, signature)) {
            throw new IllegalStateException("Signature does not match Transaction Content");
        }

        byte[] transactionHash = HashUtil.h256(rlpEncoding);

        if (destination == null) {
            return
                InternalTransaction.contractCreateInvokableTransaction(
                    RejectedStatus.NOT_REJECTED,
                    sender,
                    nonce,
                    value,
                    data,
                    energyLimit,
                    energyPrice,
                    transactionHash);
        } else {
            return
                InternalTransaction.contractCallInvokableTransaction(
                    RejectedStatus.NOT_REJECTED,
                    sender,
                    destination,
                    nonce,
                    value,
                    data,
                    energyLimit,
                    energyPrice,
                    transactionHash);
        }
    }
}
