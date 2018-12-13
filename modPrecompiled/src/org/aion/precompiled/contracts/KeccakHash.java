package org.aion.precompiled.contracts;

import static org.aion.crypto.HashUtil.keccak256;

import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledContract;

public class KeccakHash implements PrecompiledContract {
    private static final long DEFAULT_COST = 100L;

    public KeccakHash() {}

    /**
     * Returns the hash of given input
     *
     * <p>input is defined as [nb input byte array] n > 0
     *
     * <p>the returned hash is in ContractExecutionResult.getOutput
     */
    public PrecompiledTransactionResult execute(byte[] input, long nrg) {
        // check input nrg
        long additionalNRG = Math.round(Math.sqrt(input.length));
        if (nrg < DEFAULT_COST + additionalNRG)
            return new PrecompiledTransactionResult(PrecompiledResultCode.OUT_OF_NRG, 0);

        // check length
        if (input.length < 1)
            return new PrecompiledTransactionResult(
                    PrecompiledResultCode.FAILURE,
                    nrg - DEFAULT_COST,
                    "input too short".getBytes());

        byte[] hash = keccak256(input);
        return new PrecompiledTransactionResult(
                PrecompiledResultCode.SUCCESS, nrg - DEFAULT_COST - additionalNRG, hash);
    }
}
