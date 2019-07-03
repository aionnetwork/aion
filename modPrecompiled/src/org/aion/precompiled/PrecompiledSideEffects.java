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
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;

public class PrecompiledSideEffects {

    private Set<AionAddress> deleteAccounts = new HashSet<>();
    private List<InternalTransaction> internalTxs = new ArrayList<>();
    private List<Log> logs = new ArrayList<>();

    public void addToDeletedAddresses(AionAddress address) {
        deleteAccounts.add(address);
    }

    /**
     * Adds the collection addresses to the set of deleted accounts if addresses is non-null.
     *
     * @param addresses The addressed to add to the set of deleted accounts.
     */
    public void addAllToDeletedAddresses(Collection<AionAddress> addresses) {
        for (AionAddress addr : addresses) {
            if (addr != null) {
                addToDeletedAddresses(addr);
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
                addLog(log);
            }
        }
    }

    /**
     * Adds an internal transaction, tx, to the internal transactions list.
     *
     * @param tx The internal transaction to add.
     */
    public void addInternalTransaction(InternalTransaction tx) {
        internalTxs.add(tx);
    }

    /**
     * Adds a collection of internal transactions, txs, to the internal transactions list.
     *
     * @param txs The collection of internal transactions to add.
     */
    public void addInternalTransactions(List<InternalTransaction> txs) {
        for (InternalTransaction tx : txs) {
            if (tx != null) {
                addInternalTransaction(tx);
            }
        }
    }

    public void merge(PrecompiledSideEffects other) {
        addInternalTransactions(other.getInternalTransactions());
        addAllToDeletedAddresses(other.getAddressesToBeDeleted());
        addLogs(other.getExecutionLogs());
    }

    public List<AionAddress> getAddressesToBeDeleted() {
        return new ArrayList<>(deleteAccounts);
    }

    public List<Log> getExecutionLogs() {
        return logs;
    }

    /**
     * Returns the internal transactions.
     *
     * @return the internal transactions.
     */
    public List<InternalTransaction> getInternalTransactions() {
        return internalTxs;
    }
}
