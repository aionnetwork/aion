package org.aion.mcf.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.ArrayList;
import java.util.List;
import org.aion.interfaces.Bytesable;
import org.aion.interfaces.tx.Transaction;
import org.aion.interfaces.tx.TxReceipt;
import org.aion.mcf.vm.types.Bloom;
import org.aion.vm.api.interfaces.IExecutionLog;

public abstract class AbstractTxReceipt<TX extends Transaction>
        implements Bytesable<Object>, TxReceipt<TX, IExecutionLog> {

    protected TX transaction;

    protected byte[] postTxState = EMPTY_BYTE_ARRAY;

    protected Bloom bloomFilter = new Bloom();
    protected List<IExecutionLog> logInfoList = new ArrayList<>();

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

    public List<IExecutionLog> getLogInfoList() {
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

    public void setLogs(List<IExecutionLog> logInfoList) {
        if (logInfoList == null) {
            return;
        }
        this.logInfoList = logInfoList;

        for (IExecutionLog loginfo : logInfoList) {
            bloomFilter.or(loginfo.getBloomFilterForLog());
        }
        rlpEncoded = null;
    }

    public void setTransaction(TX transaction) {
        this.transaction = transaction;
    }

    public TX getTransaction() {
        if (transaction == null) {
            throw new NullPointerException(
                    "Transaction is not initialized. Use TransactionInfo and BlockStore to setup Transaction instance");
        }
        return transaction;
    }
}
