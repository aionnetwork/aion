package org.aion.precompiled.contracts;

import javax.annotation.Nonnull;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.TransactionStatus;

public class TXHashContract implements PrecompiledContract {
    public static final long COST = 20L;

    private final PrecompiledTransactionContext context;

    public TXHashContract(@Nonnull final PrecompiledTransactionContext _context) {
        context = _context;
    }

    @Override
    public PrecompiledTransactionResult execute(byte[] input, long nrgLimit) {

        long nrgLeft = nrgLimit - COST;

        if (nrgLeft < 0) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("OUT_OF_NRG"), 0, null);
        }

        return new PrecompiledTransactionResult(
            TransactionStatus.successful(), nrgLeft, context.copyOfOriginTransactionHash());
    }
}
