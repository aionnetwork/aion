package org.aion.mcf.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.aion.base.AionTransaction;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.LogUtility;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.Bytesable;
import org.apache.commons.lang3.ArrayUtils;

/** aion transaction receipt class. */
public class AionTxReceipt implements Bytesable<Object> {
    private AionTransaction transaction;

    private byte[] postTxState = EMPTY_BYTE_ARRAY;

    private Bloom bloomFilter = new Bloom();
    private List<Log> logInfoList = new ArrayList<>();

    private byte[] executionResult = EMPTY_BYTE_ARRAY;
    private String error = "";
    /* TX Receipt in encoded form */
    private byte[] rlpEncoded;

    private long energyUsed;

    public AionTxReceipt() {}

    public AionTxReceipt(byte[] rlp) {

        RLPList params = RLP.decode2(rlp);
        RLPList receipt = (RLPList) params.get(0);

        RLPItem postTxStateRLP = (RLPItem) receipt.get(0);
        RLPItem bloomRLP = (RLPItem) receipt.get(1);
        RLPList logs = (RLPList) receipt.get(2);
        RLPItem result = (RLPItem) receipt.get(3);

        postTxState = ArrayUtils.nullToEmpty(postTxStateRLP.getRLPData());
        bloomFilter = new Bloom(bloomRLP.getRLPData());
        executionResult =
                (executionResult = result.getRLPData()) == null
                        ? EMPTY_BYTE_ARRAY
                        : executionResult;
        energyUsed = ByteUtil.byteArrayToLong(receipt.get(4).getRLPData());

        if (receipt.size() > 5) {
            byte[] errBytes = receipt.get(5).getRLPData();
            error = errBytes != null ? new String(errBytes, StandardCharsets.UTF_8) : "";
        }

        for (RLPElement log : logs) {
            Log logInfo = LogUtility.decodeLog(log.getRLPData());
            if (logInfo != null) {
                logInfoList.add(logInfo);
            }
        }

        rlpEncoded = rlp;
    }

    public AionTxReceipt(byte[] postTxState, Bloom bloomFilter, List<Log> logInfoList) {
        this.postTxState = postTxState;
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
    }

    public byte[] getPostTxState() {
        return postTxState;
    }

    public byte[] getTransactionOutput() {
        return executionResult;
    }

    public Bloom getBloomFilter() {
        return bloomFilter;
    }

    public List<Log> getLogInfoList() {
        return logInfoList;
    }

    public boolean isSuccessful() {
        return error.isEmpty();
    }

    public String getError() {
        return error;
    }

    public void setPostTxState(byte[] postTxState) {
        this.postTxState = postTxState;
        rlpEncoded = null;
    }

    public void setExecutionResult(byte[] executionResult) {
        this.executionResult = executionResult;
        rlpEncoded = null;
    }

    /**
     * TX recepit 's error is empty when constructed. it use empty to identify if there are error
     * msgs instead of null.
     */
    public void setError(String error) {
        if (error == null) {
            return;
        }
        this.error = error;
    }

    public void setLogs(List<Log> logInfoList) {
        if (logInfoList == null) {
            return;
        }
        this.logInfoList = logInfoList;

        for (Log loginfo : logInfoList) {
            bloomFilter.or(LogUtility.createBloomFilterForLog(loginfo));
        }
        rlpEncoded = null;
    }

    public void setTransaction(AionTransaction transaction) {
        this.transaction = transaction;
    }

    public AionTransaction getTransaction() {
        if (transaction == null) {
            throw new NullPointerException(
                    "Transaction is not initialized. Use TransactionInfo and BlockStore to setup Transaction instance");
        }
        return transaction;
    }
    /**
     * Used for Receipt trie hash calculation. Should contain only the following items encoded:
     * [postTxState, bloomFilter, logInfoList]
     */
    public byte[] getReceiptTrieEncoded() {
        return getEncoded(true);
    }

    /** Used for serialization, contains all the receipt data encoded */
    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            rlpEncoded = getEncoded(false);
        }

        return rlpEncoded;
    }

    public void setNrgUsed(long l) {
        this.energyUsed = l;
    }

    public long getEnergyUsed() {
        return this.energyUsed;
    }

    public byte[] toBytes() {
        return getEncoded();
    }

    public AionTxReceipt fromBytes(byte[] bs) {
        return new AionTxReceipt(bs);
    }

    /**
     * Encodes the receipt, depending on whether the intended destination is for calculation of the
     * receipts trie, or for storage purposes. In effect the receipt stores more information than
     * what is defined in the <a href="http://http://yellowpaper.io/">YP</a>.
     *
     * @param receiptTrie true if target is "strictly" adhering to YP
     * @return {@code rlpEncoded} byte array representing the receipt
     */
    private byte[] getEncoded(boolean receiptTrie) {

        byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.data);

        final byte[] logInfoListRLP;
        if (logInfoList != null) {
            byte[][] logInfoListE = new byte[logInfoList.size()][];

            int i = 0;
            for (Log logInfo : logInfoList) {
                logInfoListE[i] = LogUtility.encodeLog(logInfo);
                ++i;
            }
            logInfoListRLP = RLP.encodeList(logInfoListE);
        } else {
            logInfoListRLP = RLP.encodeList();
        }

        return receiptTrie
                ? RLP.encodeList(postTxStateRLP, bloomRLP, logInfoListRLP)
                : RLP.encodeList(
                        postTxStateRLP,
                        bloomRLP,
                        logInfoListRLP,
                        RLP.encodeElement(executionResult),
                        RLP.encodeLong(energyUsed),
                        RLP.encodeElement(error.getBytes(StandardCharsets.UTF_8)));
    }

    /** TODO: check that this is valid, should null == valid? */
    public boolean isValid() {
        return this.error == null || this.error.equals("");
    }

    @Override
    public String toString() {
        return "TransactionReceipt["
                + "\n  , postTxState="
                + Hex.toHexString(postTxState)
                + "\n  , error="
                + error
                + "\n  , executionResult="
                + Hex.toHexString(executionResult)
                + "\n  , bloom="
                + bloomFilter.toString()
                + "\n  , logs="
                + logInfoList
                + "\n  , nrgUsed="
                + this.energyUsed
                + ']';
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;

        if (!(other instanceof AionTxReceipt)) return false;

        AionTxReceipt o = (AionTxReceipt) other;

        if (!Arrays.equals(this.executionResult, o.executionResult)) return false;

        if (!Arrays.equals(this.postTxState, o.postTxState)) return false;

        if (!Objects.equals(this.error, o.error)) return false;

        return Objects.equals(this.bloomFilter, o.bloomFilter);
    }
}
