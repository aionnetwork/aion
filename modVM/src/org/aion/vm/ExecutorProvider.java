package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;

public interface ExecutorProvider {

    /**
     * @implNote note that this essentially means that a new precompiled contract
     * is created each time the contract is executed. Reasoning behind this is
     * that {@code ExecutionContext} is specific to a particular call.
     *
     * @implNote no real good reason for passing in the {@code ExecutionContext}
     * in the constructor, rather than the execution call. Just more convenient as it
     * disrupts the least amount of code paths
     *
     * @param context
     * @param track
     * @return @{code precompiled contract} if available {@code null} otherwise
     */
    IPrecompiledContract getPrecompiledContract(ExecutionContext context, IRepositoryCache track);

    VirtualMachine getVM();
}
