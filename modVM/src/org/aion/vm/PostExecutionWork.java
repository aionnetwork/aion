package org.aion.vm;

import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.aion.zero.types.AionTxExecSummary;

/**
 * A functional interface that specifies post-execution "work". That is, the that must be done directly
 * after a transaction has been executed.
 */
@FunctionalInterface
public interface PostExecutionWork {

    /**
     * Performs the specified post-execution work that is to be done immediately after a transaction
     * has been executed.
     *
     * This work may do whatever it needs to the provided inputs.
     *
     * The work must return the amount of energy that this transaction will use in its block so that
     * the remaining block energy may be updated correctly. Note that this value may not always be
     * relevant to the component of the kernel that wants this work done, and so it is also able to
     * return 0 in that case.
     *
     * @param kernel The interface into the kernel's underlying storage.
     * @param summary The transaction execution summary.
     * @param transaction The transaction.
     * @param blockEnergyRemaining The amount of energy remaining in the block <b>prior</b> to execution.
     * @return The amount of energy that this transaction uses in its block.
     */
    long doExecutionWork(KernelInterface kernel, AionTxExecSummary summary, TransactionInterface transaction, long blockEnergyRemaining);

}
