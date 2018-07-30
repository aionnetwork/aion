/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.vm;

import org.aion.base.type.Address;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.types.AionInternalTx;

import java.util.*;

/**
 * An internal helper class which holds all the dynamically generated effects:
 * <p>
 * <ol>
 * <li>logs created</li>
 * <li>internal txs created</li>
 * <li>account deleted</li>
 * <p>
 *
 * @author yulong
 */
public class ExecutionHelper {

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

    /**
     * Create a new execution result.
     */
    public ExecutionHelper() {
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

    /**
     * Reject all internal transactions.
     */
    public void rejectInternalTransactions() {
        for (AionInternalTx tx : getInternalTransactions()) {
            tx.reject();
        }
    }

    /**
     * Merge another execution result.
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
}