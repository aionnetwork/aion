package org.aion.vm;

import org.aion.base.type.IExecutionResult;

/** A pre-compiled contract interface. */
public interface IPrecompiledContract {

    /**
     * Returns the result of executing the pre-compiled contract. The contract will be executed
     * using the input arguments input and the energy limit nrgLimit.
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the contract execution result.
     */
    IExecutionResult execute(byte[] input, long nrgLimit);
}
