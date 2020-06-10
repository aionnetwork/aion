package org.aion.base;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.fastvm.FvmConstants;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPList;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Transaction;
import org.slf4j.Logger;

public final class TxUtil {

    private TxUtil() {
        throw new AssertionError("TxUtil may not be instantiated");
    }

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    public static long calculateTransactionCost(byte[] data, boolean isCreate) {
        long zeroes = zeroBytesInData(data);
        long nonZeroes = data.length - zeroes;

        return (isCreate ? FvmConstants.CREATE_TRANSACTION_FEE : 0)
                + FvmConstants.TRANSACTION_BASE_FEE
                + zeroes * FvmConstants.ZERO_BYTE_FEE
                + nonZeroes * FvmConstants.NONZERO_BYTE_FEE;
    }

    public static long calculateTransactionCost(AionTransaction tx) {
        return calculateTransactionCost(tx.getData(), tx.isContractCreationTransaction());
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
        return calculateContractAddress(tx.senderAddress.toByteArray(), tx.nonce);
    }

    public static AionAddress calculateContractAddress(AionTransaction tx) {
        if (tx.getDestinationAddress() != null) {
            return null;
        }
        return calculateContractAddress(tx.getSenderAddress().toByteArray(), tx.getNonceBI());
    }

    public static AionAddress calculateContractAddress(InternalTransaction itx) {
        if (itx.destination != null) {
            return null;
        }
        return calculateContractAddress(itx.sender.toByteArray(), itx.senderNonce);
    }

    /** Calculates the address as per the QA2 definitions */
    public static AionAddress calculateContractAddress(byte[] addr, BigInteger nonce) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(AddressSpecs.A0_IDENTIFIER);

        byte[] encSender = RLP.encodeElement(addr);
        byte[] encNonce = RLP.encodeBigInteger(nonce);

        buf.put(HashUtil.h256(RLP.encodeList(encSender, encNonce)), 1, 31);
        return new AionAddress(buf.array());
    }

    /**
     * The value to use for the {@link #RLP_TX_EXTENSIONS} field if beacon hash
     * extension is used.
     */
    public static final byte BEACON_HASH_EXTENSION = 1;

    public static final int RLP_TX_NONCE = 0,
            RLP_TX_TO = 1,
            RLP_TX_VALUE = 2,
            RLP_TX_DATA = 3,
            RLP_TX_TIMESTAMP = 4,
            RLP_TX_NRG = 5,
            RLP_TX_NRGPRICE = 6,
            RLP_TX_TYPE = 7,
            RLP_TX_SIG = 8,
            RLP_TX_EXTENSIONS = 9,
            RLP_TX_BEACON_HASH = 10;

    /**
     * Decode the given rlp encoding into an {@link AionTransaction}.
     *
     * The given encoding is expected to always be an RLP list of size 10 or 11.
     * The expected elements and their indices in the list :
     *
     * {@link #RLP_TX_NONCE} - nonce
     * {@link #RLP_TX_TO} - destination address
     * {@link #RLP_TX_VALUE} - transfer value
     * {@link #RLP_TX_DATA} - transaction data/input
     * {@link #RLP_TX_TIMESTAMP} - timestamp
     * {@link #RLP_TX_NRG} - energy
     * {@link #RLP_TX_NRGPRICE} - energy price
     * {@link #RLP_TX_TYPE} - transaction type
     * {@link #RLP_TX_SIG} - signature
     * {@link #RLP_TX_BEACON_HASH} - Optional: beacon hash.  If absent,
     *                               this element is not present in the list.
     *
     * @param rlpEncoding RLP encoding of an Aion transaction
     * @return Aion Transaction represented by the given RLP encoding
     */
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

        final byte[] beaconHash;
        if(tx.size() - 1 >= RLP_TX_EXTENSIONS) {
            // there are extensions
            // today -- the only one that exists is beacon hash extension
            byte extensions = tx.get(RLP_TX_EXTENSIONS).getRLPData()[0];
            if(extensions != BEACON_HASH_EXTENSION) {
                throw new IllegalArgumentException("TxUtil#decode: unknown extension value: " +
                        Byte.toString(extensions));
            }

            // the beacon hash should be present -- check that it really does
            if(tx.size() - 1 != RLP_TX_BEACON_HASH) {
                // malformed
                throw new IllegalArgumentException("TxUtil#decode: malformed encoding: " +
                        "BEACON_HASH_EXTENSION was specified, but no beacon hash was provided");
            }
            beaconHash = tx.get(RLP_TX_BEACON_HASH).getRLPData();
        } else {
            // the beacon hash is absent
            beaconHash = null;
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
                rlpEncoding,
                beaconHash);
        }
        catch (Exception e) {
            LOG.error("tx -> invalid parameter decoded in rlpEncoding");
            return null;
        }
    }

    public static AionTransaction decodeUsingRlpSharedList(byte[] rlpEncoding) {
        Objects.requireNonNull(rlpEncoding);

        try {
            SharedRLPList decodedTxList = RLP.decode2SharedList(rlpEncoding);

            RLPElement element = decodedTxList.get(0);
            if (!element.isList()) {
                throw new IllegalArgumentException("The decoded data is not a list");
            }

            SharedRLPList tx = (SharedRLPList) element;

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

            final byte[] beaconHash;
            if(tx.size() - 1 >= RLP_TX_EXTENSIONS) {
                // there are extensions
                // today -- the only one that exists is beacon hash extension
                byte extensions = tx.get(RLP_TX_EXTENSIONS).getRLPData()[0];
                if(extensions != BEACON_HASH_EXTENSION) {
                    throw new IllegalArgumentException("TxUtil#decode: unknown extension value: " +
                        Byte.toString(extensions));
                }

                // the beacon hash should be present -- check that it really does
                if(tx.size() - 1 != RLP_TX_BEACON_HASH) {
                    // malformed
                    throw new IllegalArgumentException("TxUtil#decode: malformed encoding: " +
                        "BEACON_HASH_EXTENSION was specified, but no beacon hash was provided");
                }
                beaconHash = tx.get(RLP_TX_BEACON_HASH).getRLPData();
            } else {
                // the beacon hash is absent
                beaconHash = null;
            }

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
                rlpEncoding,
                beaconHash);
        } catch (Exception e) {
            LOG.error("tx -> unable to decode rlpEncoding", e);
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
            byte type,
            byte[] beaconHash) {
        // see https://aionnetwork.atlassian.net/wiki/spaces/TE/pages/292389035/Transaction+RLP+Encoding
        // for decoding/encoding rules

        byte[] nonceEncoded = RLP.encodeElement(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeElement(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] timeStampEncoded = RLP.encodeElement(timeStamp);
        byte[] energyLimitEncoded = RLP.encodeLong(energyLimit);
        byte[] energyPriceEncoded = RLP.encodeLong(energyPrice);
        byte[] typeEncoded = RLP.encodeByte(type);

        if(beaconHash == null) {
            return RLP.encodeList(
                    nonceEncoded,
                    destinationEncoded,
                    valueEncoded,
                    dataEncoded,
                    timeStampEncoded,
                    energyLimitEncoded,
                    energyPriceEncoded,
                    typeEncoded);
        } else {
            // may use different extensions in the future, but
            // today, the only extension is beacon hash, which
            // is represented by 0x01.
            byte[] extensionsEncoded = RLP.encodeByte(BEACON_HASH_EXTENSION);

            byte[] beaconHashEncoded = RLP.encodeElement(beaconHash);
            return RLP.encodeList(
                    nonceEncoded,
                    destinationEncoded,
                    valueEncoded,
                    dataEncoded,
                    timeStampEncoded,
                    energyLimitEncoded,
                    energyPriceEncoded,
                    typeEncoded,
                    extensionsEncoded,
                    beaconHashEncoded);
        }
    }

    /**
     * Encode the given {@link AionTransaction} into an RLP encoding.
     *
     * The only parameter allowed to be null is beaconHash.
     *
     */
    static byte[] rlpEncode(
            byte[] nonce,
            AionAddress destination,
            byte[] value,
            byte[] data,
            byte[] timeStamp,
            long energyLimit,
            long energyPrice,
            byte type,
            ISignature signature,
            byte[] beaconHash) {
        // see https://aionnetwork.atlassian.net/wiki/spaces/TE/pages/292389035/Transaction+RLP+Encoding
        // for decoding/encoding rules

        byte[] nonceEncoded = RLP.encodeElement(nonce);
        byte[] destinationEncoded = RLP.encodeElement(destination == null ? null : destination.toByteArray());
        byte[] valueEncoded = RLP.encodeElement(value);
        byte[] dataEncoded = RLP.encodeElement(data);
        byte[] timeStampEncoded = RLP.encodeElement(timeStamp);
        byte[] energyLimitEncoded = RLP.encodeLong(energyLimit);
        byte[] energyPriceEncoded = RLP.encodeLong(energyPrice);
        byte[] typeEncoded = RLP.encodeByte(type);
        byte[] signatureEncoded = RLP.encodeElement(signature.toBytes());

        if(beaconHash == null) {
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
        } else {
            // may use different extensions in the future, but
            // today, the only extension is beacon hash, which
            // is represented by 0x01.
            byte[] extensionsEncoded = RLP.encodeByte(BEACON_HASH_EXTENSION);
            byte[] beaconHashEncoded = RLP.encodeElement(beaconHash);
            return RLP.encodeList(
                    nonceEncoded,
                    destinationEncoded,
                    valueEncoded,
                    dataEncoded,
                    timeStampEncoded,
                    energyLimitEncoded,
                    energyPriceEncoded,
                    typeEncoded,
                    signatureEncoded,
                    extensionsEncoded,
                    beaconHashEncoded);
        }
    }
}
