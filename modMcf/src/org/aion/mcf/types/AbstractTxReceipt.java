package org.aion.mcf.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.tx.TxReceipt;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.LogUtility;
import org.aion.types.Log;
import org.aion.util.types.Bytesable;


public abstract class AbstractTxReceipt implements Bytesable<Object>, TxReceipt<Log> {

    protected AionTransaction transaction;

    protected byte[] postTxState = EMPTY_BYTE_ARRAY;

    protected Bloom bloomFilter = new Bloom();
    protected List<Log> logInfoList = new ArrayList<>();

    protected byte[] executionResult = EMPTY_BYTE_ARRAY;
    protected String error = "";
    /* TX Receipt in encoded form */
    protected byte[] rlpEncoded;

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

    public abstract boolean isValid();

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
}
