package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.InternalTransaction.RejectedStatus;
import org.aion.util.conversions.Hex;
import org.aion.base.AionTxReceipt;

public class AionTxInfo {
    // TODO AKI-316: refactor AionTxInfo instance variable access

    // note that the receipt is modified to set the transaction
    private final AionTxReceipt receipt;
    private final byte[] blockHash;
    private final int index;
    private final List<InternalTransaction> internalTransactions;
    private final boolean createdWithInternalTransactions;

    /** @implNote Instance creation should be done through the static factory methods. */
    private AionTxInfo(AionTxReceipt receipt, byte[] blockHash, int index, List<InternalTransaction> internalTransactions, boolean createdWithInternalTransactions) {
        this.receipt = receipt;
        this.blockHash = blockHash;
        this.index = index;
        this.internalTransactions = internalTransactions;
        this.createdWithInternalTransactions = createdWithInternalTransactions;
    }

    /**
     * Creates an instance with the base data: receipt, block hash and index. Does not stored
     * information regarding internal transactions.
     */
    public static AionTxInfo newInstance(AionTxReceipt receipt, byte[] blockHash, int index) {
        return new AionTxInfo(receipt, blockHash, index, null, false);
    }

    /** Creates an instance with receipt, block hash, index and internal transactions. */
    public static AionTxInfo newInstanceWithInternalTransactions(AionTxReceipt receipt, byte[] blockHash, int index, List<InternalTransaction> internalTransactions) {
        return new AionTxInfo(receipt, blockHash, index, internalTransactions, true);
    }

    // define the list sizes allowed by the info encoding
    public static final int SIZE_OF_OLD_ENCODING = 3,
            SIZE_WITH_BASE_DATA = 4,
            SIZE_WITH_INTERNAL_TRANSACTIONS = 5;

    // define the order in which the info data is RLP encoded
    private static final int INDEX_RECEIPT = 0,
            INDEX_BLOCK_HASH = 1,
            INDEX_TX_INDEX = 2,
            INDEX_CREATE_FLAG = 3,
            INDEX_INTERNAL_TX = 4;

    /**
     * Creates an instance based on a given encoding. Supports encodings of sizes:
     *
     * <ol>
     *   <li>{@link #SIZE_OF_OLD_ENCODING} for compatibility with encodings prior to the addition of
     *       internal transactions;
     *   <li>{@link #SIZE_WITH_BASE_DATA} for encodings where there are no internal transactions
     *       (either because they are not being stored or because they were not generated);
     *   <li>{@link #SIZE_WITH_INTERNAL_TRANSACTIONS} for encodings where the internal transactions
     *       are stored.
     * </ol>
     */
    public static AionTxInfo newInstanceFromEncoding(byte[] rlp) {
        RLPList params = RLP.decode2(rlp);
        RLPList txInfo = (RLPList) params.get(0);

        AionTxReceipt receipt = new AionTxReceipt(txInfo.get(INDEX_RECEIPT).getRLPData());
        byte[] blockHash = txInfo.get(INDEX_BLOCK_HASH).getRLPData();

        int index;
        RLPItem indexRLP = (RLPItem) txInfo.get(INDEX_TX_INDEX);
        if (indexRLP.getRLPData() == null) {
            index = 0;
        } else {
            index = new BigInteger(1, indexRLP.getRLPData()).intValue();
        }

        boolean createdWithInternalTx;
        List<InternalTransaction> internalTransactions;

        switch (txInfo.size()) {
            case SIZE_OF_OLD_ENCODING:
                // old encodings are incomplete since internal tx were not stored
                createdWithInternalTx = false;
                internalTransactions = null;
                break;
            case SIZE_WITH_BASE_DATA:
                // read the completeness flag from storage
                createdWithInternalTx = txInfo.get(INDEX_CREATE_FLAG).getRLPData().length == 1;
                internalTransactions = null;
                break;
            case SIZE_WITH_INTERNAL_TRANSACTIONS:
                // read the completeness flag from storage
                createdWithInternalTx = txInfo.get(INDEX_CREATE_FLAG).getRLPData().length == 1;
                // decode the internal transactions
                internalTransactions = new ArrayList<>();
                RLPList internalTxRlp = (RLPList) txInfo.get(INDEX_INTERNAL_TX);
                for (RLPElement item : internalTxRlp) {
                    internalTransactions.add(fromRlp((RLPList) item));
                }
                break;
            default:
                // incorrect encoding
                return null;
        }

        return new AionTxInfo(receipt, blockHash, index, internalTransactions, createdWithInternalTx);
    }

    public void setTransaction(AionTransaction tx) {
        receipt.setTransaction(tx);
    }

    /* [receipt, blockHash, index] */
    public byte[] getEncoded() {

        byte[] receiptRLP = this.receipt.toBytes();
        byte[] blockHashRLP = RLP.encodeElement(blockHash);
        byte[] indexRLP = RLP.encodeInt(index);
        byte[] completeRLP = RLP.encodeByte(createdWithInternalTransactions ? (byte) 1 : (byte) 0);

        byte[] rlpEncoded;

        if (hasInternalTransactions()) {
            byte[][] internal = new byte[internalTransactions.size()][];
            for (int i = 0; i < internalTransactions.size(); i++) {
                internal[i] = toRlp(internalTransactions.get(i));
            }
            rlpEncoded = RLP.encodeList(receiptRLP, blockHashRLP, indexRLP, completeRLP, RLP.encodeList(internal));
        } else {
            rlpEncoded = RLP.encodeList(receiptRLP, blockHashRLP, indexRLP, completeRLP);
        }

        return rlpEncoded;
    }

    public AionTxReceipt getReceipt() {
        return receipt;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public int getIndex() {
        return index;
    }

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    /** Indicates if the internal transactions were passed to the constructor or not. */
    public boolean isCreatedWithInternalTransactions() {
        return createdWithInternalTransactions;
    }

    public boolean hasInternalTransactions() {
        return !(internalTransactions == null || internalTransactions.isEmpty());
    }

    private static byte[] toRlp(InternalTransaction transaction) {

        byte[] from = RLP.encodeElement(transaction.sender.toByteArray());
        byte[] to = RLP.encodeElement(transaction.isCreate ? null : transaction.destination.toByteArray());
        byte[] nonce = RLP.encodeElement(transaction.senderNonce.toByteArray());
        byte[] value = RLP.encodeElement(transaction.value.toByteArray());
        byte[] data = RLP.encodeElement(transaction.copyOfData());
        // encodeLong has non-standard encoding therefore avoiding its use here
        byte[] limit = RLP.encode(transaction.energyLimit);
        byte[] price = RLP.encode(transaction.energyPrice);
        byte[] isRejected = RLP.encodeByte(transaction.isRejected ? (byte) 1 : (byte) 0);

        // the order must match the defined index variables below
        return RLP.encodeList(from, to, nonce, value, data, limit, price, isRejected);
    }

    // define the order in which the internal transaction data is RLP encoded
    private static final int INDEX_FROM = 0,
            INDEX_TO = 1,
            INDEX_NONCE = 2,
            INDEX_VALUE = 3,
            INDEX_DATA = 4,
            INDEX_LIMIT = 5,
            INDEX_PRICE = 6,
            INDEX_STATUS = 7;

    private static InternalTransaction fromRlp(RLPList encoded) {
        AionAddress from = new AionAddress(encoded.get(INDEX_FROM).getRLPData());
        AionAddress to;
        boolean isCreate;
        byte[] rlpTo = encoded.get(INDEX_TO).getRLPData();
        if (rlpTo == null || rlpTo.length == 0) {
            to = null;
            isCreate = true;
        } else {
            to = new AionAddress(rlpTo);
            isCreate = false;
        }

        BigInteger nonce = new BigInteger(1, encoded.get(INDEX_NONCE).getRLPData());
        BigInteger value = new BigInteger(1, encoded.get(INDEX_VALUE).getRLPData());
        byte[] data = encoded.get(INDEX_DATA).getRLPData();

        long energyLimit = new BigInteger(1, encoded.get(INDEX_LIMIT).getRLPData()).longValue();
        long energyPrice = new BigInteger(1, encoded.get(INDEX_PRICE).getRLPData()).longValue();
        RejectedStatus status =
                // checking the length because zero (i.e. false) decodes to empty byte array
                (encoded.get(INDEX_STATUS).getRLPData().length == 1)
                        ? RejectedStatus.REJECTED
                        : RejectedStatus.NOT_REJECTED;

        if (isCreate) {
            return InternalTransaction.contractCreateTransaction(status, from, nonce, value, data, energyLimit, energyPrice);
        } else {
            return InternalTransaction.contractCallTransaction(status, from, to, nonce, value, data, energyLimit, energyPrice);
        }
    }

    @Override
    public String toString() {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.setLength(0);
        toStringBuff.append("  ").append("index=").append(index).append("\n");
        toStringBuff.append("  ").append(receipt.toString()).append("\n");
        if (hasInternalTransactions()) {
            toStringBuff
                    .append("\n  produced ")
                    .append(internalTransactions.size())
                    .append(" internal transactions:\n");
            for (InternalTransaction itx : internalTransactions) {
                toStringBuff.append("\t- ").append(itx).append("\n");
            }
        } else {
            toStringBuff.append(
                    createdWithInternalTransactions
                            ? "\n  produced 0 internal transactions\n"
                            : "\n  internal transactions not stored\n");
        }
        toStringBuff.append("  ").append(Hex.toHexString(this.getEncoded()));

        return toStringBuff.toString();
    }
}
