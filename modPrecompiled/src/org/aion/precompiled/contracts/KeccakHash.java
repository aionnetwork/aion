package org.aion.precompiled.contracts;

import static org.aion.crypto.HashUtil.keccak256;

import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.types.TransactionStatus;

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
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("OUT_OF_NRG"), 0);

        // check length
        if (input.length < 1)
            return new PrecompiledTransactionResult(
                    TransactionStatus.nonRevertedFailure("FAILURE"),
                    nrg - DEFAULT_COST,
                    "input too short".getBytes());

        byte[] hash = keccak256(input);
        return new PrecompiledTransactionResult(
                TransactionStatus.successful(), nrg - DEFAULT_COST - additionalNRG, hash);
    }
}
