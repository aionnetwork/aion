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
package org.aion.precompiled;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.vm.api.interfaces.TransactionSideEffects;

public class PrecompiledSideEffects implements TransactionSideEffects {

    private Set<Address> deleteAccounts = new HashSet<>();
    private List<InternalTransactionInterface> internalTxs = new ArrayList<>();
    private List<IExecutionLog> logs = new ArrayList<>();

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
     * Returns the internal transactions.
     *
     * @return the internal transactions.
     */
    @Override
    public List<InternalTransactionInterface> getInternalTransactions() {
        return internalTxs;
    }
}
