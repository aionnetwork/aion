package org.aion.precompiled.type;

import java.util.Arrays;
import org.aion.types.AionAddress;
import org.aion.vm.api.interfaces.TransactionSideEffects;

public final class PrecompiledTransactionContext {

    public static final int CREATE = 3;
    public final AionAddress destinationAddress;
    public final AionAddress originAddress;
    public final AionAddress senderAddress;
    public final int stackDepth;
    public final long blockNumber;
    public final long transactionEnergy;
    public final TransactionSideEffects sideEffects;
    private final byte[] originTransactionHash;
    private final byte[] transactionHash;

    public PrecompiledTransactionContext(
            AionAddress destinationAddress,
            AionAddress originAddress,
            AionAddress senderAddress,
            TransactionSideEffects sideEffects,
            byte[] originTransactionHash,
            byte[] transactionHash,
            long blockNumber,
            long transactionEnergy,
            int stackDepth) {
        this.destinationAddress = destinationAddress;
        this.originAddress = originAddress;
        this.senderAddress = senderAddress;
        this.sideEffects = sideEffects;
        this.originTransactionHash =
                originTransactionHash == null
                        ? null
                        : Arrays.copyOf(originTransactionHash, originTransactionHash.length);
        this.transactionHash =
                transactionHash == null
                        ? null
                        : Arrays.copyOf(transactionHash, transactionHash.length);
        this.blockNumber = blockNumber;
        this.transactionEnergy = transactionEnergy;
        this.stackDepth = stackDepth;
    }

    // TODO -- note that origin hash & tx hash are the same.

    public byte[] copyOfOriginTransactionHash() {
        return this.originTransactionHash == null
                ? null
                : Arrays.copyOf(this.originTransactionHash, this.originTransactionHash.length);
    }

    public byte[] copyOfTransactionHash() {
        return this.transactionHash == null
                ? null
                : Arrays.copyOf(this.transactionHash, this.transactionHash.length);
    }
}
