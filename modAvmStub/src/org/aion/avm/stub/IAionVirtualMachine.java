package org.aion.avm.stub;

import org.aion.types.Transaction;

/**
 * The AVM.
 *
 * The AVM is not thread-safe. It is the responsibility of the caller to interact with it safely
 * in a multi-threaded environment.
 */
public interface IAionVirtualMachine {

    /**
     * Returns future results of the execution of the specified transactions.
     *
     * @param externalState The current state of the world.
     * @param transactions The transactions to execute.
     * @param avmExecutionType The execution type.
     * @param cachedBlockNumber The cached block number.
     * @return the results as futures.
     */
    public IAvmFutureResult[] run(IAvmExternalState externalState, Transaction[] transactions, AvmExecutionType avmExecutionType, long cachedBlockNumber);

    /**
     * Shuts down the AVM.
     */
    public void shutdown();
}
