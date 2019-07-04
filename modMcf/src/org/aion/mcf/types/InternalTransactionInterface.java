package org.aion.mcf.types;

import org.aion.base.TransactionInterface;

/**
 * An internal transaction is a transaction spawned as a result of the execution of some other
 * transaction, referred to as its parent.
 *
 * <p>An internal transaction is never submitted directly into a virtual machine. Transactions
 * submitted directly to a virtual machine are known as external transactions, or just transactions
 * (see {@link TransactionInterface}).
 *
 * <p>All internal transactions are spawned by the logic of some external transaction or else by the
 * logic of another internal transaction, whose original ancestor is an external transaction.
 *
 * <p>An internal transaction has a concept of being 'rejected' that is slightly different than this
 * concept for an external transaction. An internal transaction is rejected if at least one of any
 * of its ancestor transactions was un-successful.
 */
public interface InternalTransactionInterface extends TransactionInterface {

    /**
     * Returns {@code true} if, and only if, this internal transaction is rejected.
     *
     * @return True if this internal transaction is rejected.
     */
    boolean isRejected();

    /**
     * Causes this internal transaction to be marked as rejected so that {@code isRejected() ==
     * true}. Once an internal transaction is marked as rejected it cannot be un-marked.
     */
    void markAsRejected();
}
