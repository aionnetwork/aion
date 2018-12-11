package org.aion.vm;

import org.aion.base.db.IRepositoryCache;

/**
 * High-level interface of Aion virtual machine.
 *
 * @author yulong
 */
public interface VirtualMachine {

    /**
     * Run the given code, under the specified context.
     *
     * @param code byte code
     * @param ctx the execution context
     * @param track state repository track
     * @return the execution result
     */
    ExecutionResult run(byte[] code, ExecutionContext ctx, IRepositoryCache track);
}
