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
package org.aion.vm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.vm.api.interfaces.TransactionSideEffects;

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
public class SideEffects implements TransactionSideEffects {

    private Set<Address> deleteAccounts = new HashSet<>();
    private List<InternalTransactionInterface> internalTxs = new ArrayList<>();
    private List<IExecutionLog> logs = new ArrayList<>();
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

    @Override
    public void addToDeletedAddresses(Address address) {
        deleteAccounts.add(address);
    }

    /**
     * Adds the collection addresses to the set of deleted accounts if addresses is non-null.
     *
     * @param addresses The addressed to add to the set of deleted accounts.
     */
    @Override
    public void addAllToDeletedAddresses(Collection<Address> addresses) {
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
    @Override
    public void addLog(IExecutionLog log) {
        logs.add(log);
    }

    /**
     * Adds a collection of logs, logs, to the execution logs.
     *
     * @param logs The collection of logs to add to the execution logs.
     */
    @Override
    public void addLogs(Collection<IExecutionLog> logs) {
        for (IExecutionLog log : logs) {
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
    @Override
    public void addInternalTransaction(InternalTransactionInterface tx) {
        internalTxs.add(tx);
    }

    /**
     * Adds a collection of internal transactions, txs, to the internal transactions list.
     *
     * @param txs The collection of internal transactions to add.
     */
    @Override
    public void addInternalTransactions(List<InternalTransactionInterface> txs) {
        for (InternalTransactionInterface tx : txs) {
            if (tx != null) {
                this.internalTxs.add(tx);
            }
        }
    }

    @Override
    public void markAllInternalTransactionsAsRejected() {
        for (InternalTransactionInterface tx : getInternalTransactions()) {
            tx.markAsRejected();
        }
    }

    @Override
    public void merge(TransactionSideEffects other) {
        addInternalTransactions(other.getInternalTransactions());
        addAllToDeletedAddresses(other.getAddressesToBeDeleted());
        addLogs(other.getExecutionLogs());
    }

    @Override
    public List<Address> getAddressesToBeDeleted() {
        return new ArrayList<>(deleteAccounts);
    }

    @Override
    public List<IExecutionLog> getExecutionLogs() {
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
    @Override
    public List<InternalTransactionInterface> getInternalTransactions() {
        return internalTxs;
    }
}
