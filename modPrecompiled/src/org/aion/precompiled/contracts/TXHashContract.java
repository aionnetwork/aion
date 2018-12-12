package org.aion.precompiled.contracts;

import javax.annotation.Nonnull;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.api.interfaces.TransactionContext;

public class TXHashContract implements IPrecompiledContract {
    public static final long COST = 20L;

    private final TransactionContext context;

    public TXHashContract(@Nonnull final TransactionContext _context) {
        context = _context;
    }

    @Override
    public FastVmTransactionResult execute(byte[] input, long nrgLimit) {

        long nrgLeft = nrgLimit - COST;

        if (nrgLeft < 0) {
            return new FastVmTransactionResult(FastVmResultCode.OUT_OF_NRG, 0, null);
        }

        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrgLeft, context.getHashOfOriginTransaction());
    }
}
