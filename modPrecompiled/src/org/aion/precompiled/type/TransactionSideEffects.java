package org.aion.precompiled.type;

import java.util.Collection;
import java.util.List;
import org.aion.mcf.types.IExecutionLog;
import org.aion.mcf.types.InternalTransactionInterface;
import org.aion.types.AionAddress;

/**
 * The side-effects caused by executing some transaction.
 *
 * <p>These side-effects are largely effects that are not captured by the state changes of the
 * virtual machine.
 */
public interface TransactionSideEffects {

    /**
     * Merges the internal transactions, deleted accounts and logs of the specified other
     * transactions into this set of side-effects.
     *
     * @param sideEffects The other side-effects to merge into these ones.
     */
    void merge(TransactionSideEffects sideEffects);

    /** Marks all internal transactions that this transaction spawned as rejected. */
    void markAllInternalTransactionsAsRejected();

    /**
     * Adds the specified transaction to this transaction's list of internal transactions.
     *
     * @param transaction The next internal transaction to add.
     */
    void addInternalTransaction(InternalTransactionInterface transaction);

    /**
     * Adds the specified list of transactions to this transaction's list of internal transactions.
     *
     * @param transactions The next internal transactions to add.
     */
    void addInternalTransactions(List<InternalTransactionInterface> transactions);

    /**
     * Adds the specified address to the set of addresses that this transaction has caused to be
     * deleted.
     *
     * @param address A new address to be marked for deletion.
     */
    void addToDeletedAddresses(AionAddress address);

    /**
     * Adds the specified collection of addresses to the set of addresses that this transaction has
     * caused to be deleted.
     *
     * @param addresses The addresses to be marked for deletion.
     */
    void addAllToDeletedAddresses(Collection<AionAddress> addresses);

    /**
     * Adds the specified log to the set of logs that this transaction generated.
     *
     * @param log An execution log.
     */
    void addLog(IExecutionLog log);

    /**
     * Adds the specified collection of logs to the set of logs that this transaction fired off.
     *
     * @param logs The execution logs.
     */
    void addLogs(Collection<IExecutionLog> logs);

    /**
     * Returns the internal transactions that were spawned as a result of running this transaction.
     *
     * @return The internal transactions.
     */
    List<InternalTransactionInterface> getInternalTransactions();

    /**
     * Returns the addresses that are marked to be deleted.
     *
     * @return The addresses to be deleted.
     */
    List<AionAddress> getAddressesToBeDeleted();

    /**
     * Returns the logs that were fired off as a result of this transaction.
     *
     * @return The execution logs.
     */
    List<IExecutionLog> getExecutionLogs();
}
