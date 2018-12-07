package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.vm.api.TransactionResult;
import org.aion.vm.api.interfaces.TransactionContext;

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
    TransactionResult run(byte[] code, TransactionContext ctx, IRepositoryCache track);
}