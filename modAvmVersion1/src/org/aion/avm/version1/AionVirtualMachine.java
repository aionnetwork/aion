package org.aion.avm.version1;

import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.ExecutionType;
import org.aion.avm.core.FutureResult;
import org.aion.avm.core.IExternalState;
import org.aion.avm.stub.AvmExecutionType;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.version1.internal.AionCapabilities;
import org.aion.types.Transaction;

/**
 * The AVM.
 *
 * This class is not thread-safe.
 *
 * In particular, the {@code run()} method is asynchronous. The general contract is this:
 * {@code run()} cannot be invoked unless: 1) this is the first invocation, or 2) all future results
 * have been consumed from the previous {@code run()} call.
 */
public final class AionVirtualMachine implements IAionVirtualMachine {
    private final AvmImpl avm;

    private AionVirtualMachine(AvmImpl avm) {
        this.avm = avm;
    }

    /**
     * Constructs a new Avm instance and starts it up.
     *
     * @return A new AVM.
     */
    public static AionVirtualMachine createAndInitializeNewAvm() {
        return new AionVirtualMachine(CommonAvmFactory.buildAvmInstanceForConfiguration(new AionCapabilities(), new AvmConfiguration()));
    }

    /**
     * Executes the transactions and returns future results.
     *
     * @param externalState The current state of the world.
     * @param transactions The transactions to execute.
     * @param avmExecutionType The execution type.
     * @param cachedBlockNumber The cached block number.
     * @return the execution results.
     */
    @Override
    public AvmFutureResult[] run(IAvmExternalState externalState, Transaction[] transactions, AvmExecutionType avmExecutionType, long cachedBlockNumber) {
        ExecutionType executionType = toExecutionType(avmExecutionType);
        FutureResult[] results = this.avm.run(((IExternalState) externalState), transactions, executionType, cachedBlockNumber);
        return wrapAvmFutureResults(results);
    }

    /**
     * Shuts down the AVM. Once the AVM is shut down, the {@code run()} method can no longer be
     * invoked!
     */
    @Override
    public void shutdown() {
        this.avm.shutdown();
    }

    private static ExecutionType toExecutionType(AvmExecutionType executionType) {
        switch (executionType) {
            case MINING: return ExecutionType.MINING;
            case ETH_CALL: return ExecutionType.ETH_CALL;
            case ASSUME_MAINCHAIN: return ExecutionType.ASSUME_MAINCHAIN;
            case ASSUME_SIDECHAIN: return ExecutionType.ASSUME_SIDECHAIN;
            case SWITCHING_MAINCHAIN: return ExecutionType.SWITCHING_MAINCHAIN;
            case ASSUME_DEEP_SIDECHAIN: return ExecutionType.ASSUME_DEEP_SIDECHAIN;
            default: throw new IllegalArgumentException("Unknown execution type: " + executionType);
        }
    }

    private static AvmFutureResult[] wrapAvmFutureResults(FutureResult[] results) {
        if (results == null) {
            throw new NullPointerException("Cannot convert null results!");
        }

        AvmFutureResult[] avmResults = new AvmFutureResult[results.length];

        int index = 0;
        for (FutureResult result : results) {
            avmResults[index] = AvmFutureResult.wrap(result);
            index++;
        }

        return avmResults;
    }
}
