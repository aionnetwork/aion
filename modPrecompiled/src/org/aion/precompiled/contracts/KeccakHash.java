package org.aion.precompiled.contracts;

import static org.aion.crypto.HashUtil.keccak256;

import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

public class KeccakHash implements IPrecompiledContract {
    private static final long DEFAULT_COST = 100L;

    public KeccakHash() {}

    /**
     * Returns the hash of given input
     *
     * <p>input is defined as [nb input byte array] n > 0
     *
     * <p>the returned hash is in ContractExecutionResult.getOutput
     */
    public ExecutionResult execute(byte[] input, long nrg) {
        // check input nrg
        long additionalNRG = Math.round(Math.sqrt(input.length));
        if (nrg < DEFAULT_COST + additionalNRG)
            return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);

        // check length
        if (input.length < 1)
            return new ExecutionResult(
                    ResultCode.FAILURE, nrg - DEFAULT_COST, "input too short".getBytes());

        byte[] hash = keccak256(input);
        return new ExecutionResult(ResultCode.SUCCESS, nrg - DEFAULT_COST - additionalNRG, hash);
    }
}
