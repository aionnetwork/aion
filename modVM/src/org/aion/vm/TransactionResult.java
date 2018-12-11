package org.aion.vm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.types.AionInternalTx;

/**
 * TransacitonResults wraps the result of a transaction execution. It mainly includes:
 *
 * <p>
 *
 * <ol>
 *   <li>logs created
 *   <li>return(revert) data
 *   <li>internal txs created
 *   <li>account deleted
 *   <li>future refund
 *       <p>All the above info could be used to build TransactionReceipt.
 *
 * @author yulong
 */
public class TransactionResult extends AbstractExecutionResult {

    public static class Call {
        final byte[] data;
        final byte[] destination;
        final byte[] value;

        public Call(byte[] data, byte[] destination, byte[] value) {
            this.data = data;
            this.destination = destination;
            this.value = value;
        }

        public byte[] getData() {
            return data;
        }

        public byte[] getDestination() {
            return destination;
        }

        public byte[] getValue() {
            return value;
        }
    }

    private Set<Address> deleteAccounts = new HashSet<>();
    private List<AionInternalTx> internalTxs = new ArrayList<>();
    private List<Log> logs = new ArrayList<>();
    private List<Call> calls = new ArrayList<>();

    /** Create a new execution result. */
    public TransactionResult() {
        super(ResultCode.SUCCESS, 0);
    }

    /**
     * Returns deleted accounts.
     *
     * @return
     */
    public List<Address> getDeleteAccounts() {
        List<Address> result = new ArrayList<>();
        for (Address acc : deleteAccounts) {
            result.add(acc);
        }
        return result;
    }

    /**
     * Adds a deleted accounts.
     *
     * @param address
     */
    public void addDeleteAccount(Address address) {
        deleteAccounts.add(address);
    }

    /**
     * Adds a collections of deleted accounts.
     *
     * @param addresses
     */
    public void addDeleteAccounts(Collection<Address> addresses) {
        for (Address address : addresses) {
            addDeleteAccount(address);
        }
    }

    /**
     * Returns logs.
     *
     * @return
     */
    public List<Log> getLogs() {
        return logs;
    }

    /**
     * Adds a log.
     *
     * @param log
     */
    public void addLog(Log log) {
        logs.add(log);
    }

    /**
     * Adds a collection of logs.
     *
     * @param logs
     */
    public void addLogs(Collection<Log> logs) {
        this.logs.addAll(logs);
    }

    /**
     * Returns calls.
     *
     * @return
     */
    public List<Call> getCalls() {
        return calls;
    }

    /**
     * Adds a call.
     *
     * @param data
     * @param destination
     * @param value
     */
    public void addCall(byte[] data, byte[] destination, byte[] value) {
        calls.add(new Call(data, destination, value));
    }

    /**
     * Returns internal transactions.
     *
     * @return
     */
    public List<AionInternalTx> getInternalTransactions() {
        return internalTxs;
    }

    /**
     * Adds an internal transaction.
     *
     * @param tx
     */
    public void addInternalTransaction(AionInternalTx tx) {
        internalTxs.add(tx);
    }

    /**
     * Adds a collection of internal transactions.
     *
     * @param txs
     */
    public void addInternalTransactions(Collection<AionInternalTx> txs) {
        internalTxs.addAll(txs);
    }

    /** Reject all internal transactions. */
    public void rejectInternalTransactions() {
        for (AionInternalTx tx : getInternalTransactions()) {
            tx.reject();
        }
    }

    /**
     * Merge another execution result.
     *
     * @param another
     */
    public void merge(TransactionResult another) {
        addInternalTransactions(another.getInternalTransactions());
        addDeleteAccounts(another.getDeleteAccounts());
        addLogs(another.getLogs());
    }
}
