package org.aion.precompiled.contracts;

import javax.annotation.Nonnull;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.api.interfaces.TransactionContext;

public class TXHashContract implements IPrecompiledContract {
    public static final long COST = 20L;

    private final TransactionContext context;

    public TXHashContract(@Nonnull final TransactionContext _context) {
        context = _context;
    }

    @Override
    public TransactionResult execute(byte[] input, long nrgLimit) {

        long nrgLeft = nrgLimit - COST;

        if (nrgLeft < 0) {
            return new TransactionResult(ResultCode.OUT_OF_ENERGY, 0, null);
        }

        return new TransactionResult(ResultCode.SUCCESS, nrgLeft, context.getHashOfOriginTransaction());
    }
}
