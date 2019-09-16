package org.aion.avm.version1;

import org.aion.avm.core.FutureResult;
import org.aion.avm.core.IExternalState;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.stub.IAvmFutureResult;
import org.aion.types.TransactionResult;

/**
 * A wrapper class that wraps an AVM future result so that it can be consumed by a third-party with
 * no explicit dependency on the AVM modules.
 */
public final class AvmFutureResult implements IAvmFutureResult {
    private final FutureResult future;

    private AvmFutureResult(FutureResult future) {
        if (future == null) {
            throw new NullPointerException("Cannot create AvmFutureResult from null future!");
        }
        this.future = future;
    }

    /**
     * Wraps the real AVM future result.
     *
     * @param future The future.
     * @return this wrapper.
     */
    public static AvmFutureResult wrap(FutureResult future) {
        return new AvmFutureResult(future);
    }

    /**
     * Returns the result.
     *
     * @return the result.
     */
    @Override
    public TransactionResult getResult() {
        return this.future.getResult();
    }

    /**
     * Commits all state changes from this future result's world state to the target world state.
     *
     * @param target The state that will absorb the changes.
     */
    @Override
    public void commitStateChangesTo(IAvmExternalState target) {
        this.future.getExternalState().commitTo((IExternalState) target);
    }

    /**
     * The exception thrown, if any, during execution.
     *
     * @return the exception.
     */
    @Override
    public Throwable getException() {
        return this.future.getException();
    }
}
