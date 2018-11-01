package org.aion.precompiled.contracts;

import javax.annotation.Nonnull;
import org.aion.base.type.IExecutionResult;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

public class TXHashContract implements IPrecompiledContract {
    public static final long COST = 500L;

    private final ExecutionContext context;

    public TXHashContract(@Nonnull final ExecutionContext _context) {
        context = _context;
    }

    @Override
    public IExecutionResult execute(byte[] input, long nrgLimit) {

        long nrgLeft = nrgLimit - COST;

        if (nrgLeft < 0) {
            return new ExecutionResult(ResultCode.OUT_OF_NRG, 0);
        }

        return new ExecutionResult(ResultCode.SUCCESS, nrgLeft, context.getOriginalTxHash());
    }
}
