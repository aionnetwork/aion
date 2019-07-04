package org.aion.mcf.types;

/**
 * The result of executing a transaction using some virtual machine.
 *
 * <p>All results fall into 1 of 3 major categories: - Success - Failed - Rejected
 *
 * <p>A reverted transaction is a Failed transaction, but special logic applies to it that requires
 * its differentiation from other failures.
 *
 * <p>A fatal result code indicates a fatal error that cannot be recovered from. This is not a
 * genuine result state but is a special case error. The kernel shuts down if a fatal error is
 * thrown, unlike each of the other cases.
 */
public interface ResultCode {

    /**
     * Returns a string representation of this result code.
     *
     * @return The string representation of this result code.
     */
    String name();

    /**
     * Returns {@code true} if, and only if, the transaction executed successfully.
     *
     * @return True if the transaction executed successfully.
     */
    boolean isSuccess();

    /**
     * Returns {@code true} if, and only if, the transaction failed. A failed transaction is a
     * transaction whose failure conditions could only be detected by running the transaction logic.
     * That is, by executing some code.
     *
     * <p>The following must be true: if {@code isRevert() == true}, then {@code isFailed() ==
     * true}.
     *
     * @return True if the transaction failed.
     */
    boolean isFailed();

    /**
     * Returns {@code true} if, and only if, the transaction was rejected. A rejected transaction is
     * a transaction whose failure conditions could be detected prior to running any transaction
     * logic (any code). Such transactions involve no work for the miner and are thus dropped from
     * the network.
     *
     * <p>An exception to this definition is an internal transaction: an internal transaction is
     * either success or rejected (though really it should be success or failed).
     *
     * @return True if the transaction was rejected.
     */
    boolean isRejected();

    /**
     * Returns {@code true} if, and only if, a fatal error occurred during the transaction execution
     * that cannot be recovered from.
     *
     * @return True if a fatal error occurred.
     */
    boolean isFatal();

    /**
     * Returns {@code true} if, and only if, the transaction was reverted. A reverted transaction is
     * a special case of a failed transaction and therefore every reverted transaction is also
     * failed. But it is distinguished because special state changes occur as a result of a reverted
     * transaction as opposed to regular failures.
     *
     * @return True if the transaction was reverted.
     */
    boolean isRevert();

    /**
     * Returns the integer representation of this result code.
     *
     * @return This code as an {@code int}.
     */
    int toInt();
}
