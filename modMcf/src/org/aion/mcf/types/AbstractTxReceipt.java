/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.mcf.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.util.ArrayList;
import java.util.List;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.Bytesable;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.api.interfaces.IExecutionLog;

public abstract class AbstractTxReceipt<TX extends ITransaction>
        implements Bytesable<Object>, ITxReceipt<TX, IExecutionLog> {

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
