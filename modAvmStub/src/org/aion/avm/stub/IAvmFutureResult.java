package org.aion.avm.stub;

import org.aion.types.TransactionResult;

/**
 * A future result of an AVM execution. Any of the methods exposed by this interface will block
 * until the transation has finished processing, and then will return.
 */
public interface IAvmFutureResult {

    /**
     * Returns the execution result. This method will block if the transaction has not yet been
     * processed.
     *
     * @return the result.
     */
    public TransactionResult getResult();

    /**
     * Commits the state changes of the world state of the avm after processing this transaction to
     * the target world state. This method blocks if the transaction has not yet been processed.
     *
     * @param target The state that will absorb the changes.
     */
    public void commitStateChangesTo(IAvmExternalState target);

    /**
     * Returns the exception thrown during execution, if any. This method will block if the
     * transaction has not yet been processed.
     *
     * @return the exception.
     */
    public Throwable getException();
}
