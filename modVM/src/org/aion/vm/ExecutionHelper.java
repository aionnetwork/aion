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
 * An internal helper class which holds all the dynamically generated effects:
 *
 * <p>
 *
 * <ol>
 *   <li>logs created
 *   <li>internal txs created
 *   <li>account deleted
 *       <p>
 *
 * @author yulong
 */
public class ExecutionHelper {

    private Set<Address> deleteAccounts = new HashSet<>();
    private List<AionInternalTx> internalTxs = new ArrayList<>();
    private List<Log> logs = new ArrayList<>();
    private List<Call> calls = new ArrayList<>();

    public static class Call {

        private final byte[] data;
        private final byte[] destination;
        private final byte[] value;

        Call(byte[] data, byte[] destination, byte[] value) {
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

    /**
     * Adds address to the set of deleted accounts if address is non-null and is not already in the
     * set.
     *
     * @param address The address to add to the deleted accounts.
     */
    public void addDeleteAccount(Address address) {
        deleteAccounts.add(address);
    }

    /**
     * Adds the collection addresses to the set of deleted accounts if addresses is non-null.
     *
     * @param addresses The addressed to add to the set of deleted accounts.
     */
    public void addDeleteAccounts(Collection<Address> addresses) {
        for (Address addr : addresses) {
            if (addr != null) {
                deleteAccounts.add(addr);
            }
        }
    }

    /**
     * Adds log to the execution logs.
     *
     * @param log The log to add to the execution logs.
     */
    public void addLog(Log log) {
        logs.add(log);
    }

    /**
     * Adds a collection of logs, logs, to the execution logs.
     *
     * @param logs The collection of logs to add to the execution logs.
     */
    public void addLogs(Collection<Log> logs) {
        for (Log log : logs) {
            if (log != null) {
                this.logs.add(log);
            }
        }
    }

    /**
     * Adds a call whose parameters are given by the parameters to this method.
     *
     * @param data The call data.
     * @param destination The call destination.
     * @param value The call value.
     */
    public void addCall(byte[] data, byte[] destination, byte[] value) {
        calls.add(new Call(data, destination, value));
    }

    /**
     * Adds an internal transaction, tx, to the internal transactions list.
     *
     * @param tx The internal transaction to add.
     */
    public void addInternalTransaction(AionInternalTx tx) {
        internalTxs.add(tx);
    }

    /**
     * Adds a collection of internal transactions, txs, to the internal transactions list.
     *
     * @param txs The collection of internal transactions to add.
     */
    public void addInternalTransactions(Collection<AionInternalTx> txs) {
        for (AionInternalTx tx : txs) {
            if (tx != null) {
                this.internalTxs.add(tx);
            }
        }
    }

    /** Rejects all internal transactions. */
    public void rejectInternalTransactions() {
        for (AionInternalTx tx : getInternalTransactions()) {
            tx.reject();
        }
    }

    /**
     * Merges another ExecutionHelper into this ExecutionHelper.
     *
     * @param other another transaction result
     * @param success whether the other transaction is success or not
     */
    public void merge(ExecutionHelper other, boolean success) {
        addInternalTransactions(other.getInternalTransactions());
        if (success) {
            addDeleteAccounts(other.getDeleteAccounts());
            addLogs(other.getLogs());
        }
    }

    /**
     * Returns the list of deleted accounts, the accounts to be deleted following a transaction.
     *
     * @return the deleted accounts list.
     */
    public List<Address> getDeleteAccounts() {
        return new ArrayList<>(deleteAccounts);
    }

    /**
     * Returns the execution logs.
     *
     * @return the execution logs.
     */
    public List<Log> getLogs() {
        return logs;
    }

    /**
     * Returns the calls.
     *
     * @return the calls.
     */
    public List<Call> getCalls() {
        return calls;
    }

    /**
     * Returns the internal transactions.
     *
     * @return the internal transactions.
     */
    public List<AionInternalTx> getInternalTransactions() {
        return internalTxs;
    }
}
