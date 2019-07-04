package org.aion.precompiled.type;

import java.util.Arrays;
import java.util.List;
import org.aion.mcf.types.InternalTransactionInterface;
import org.aion.types.AionAddress;
import org.aion.types.Log;

public final class PrecompiledTransactionContext {

    public static final int CREATE = 3;
    public final AionAddress destinationAddress;
    public final AionAddress originAddress;
    public final AionAddress senderAddress;
    public final int stackDepth;
    public final long blockNumber;
    public final long transactionEnergy;
    private final List<Log> logs;
    private final List<InternalTransactionInterface> internalTransactions;
    private final List<AionAddress> deletedAddresses;
    private final byte[] originTransactionHash;
    private final byte[] transactionHash;

    public PrecompiledTransactionContext(
            AionAddress destinationAddress,
            AionAddress originAddress,
            AionAddress senderAddress,
            List<Log> logs,
            List<InternalTransactionInterface> internalTransactions,
            List<AionAddress> deletedAddresses,
            byte[] originTransactionHash,
            byte[] transactionHash,
            long blockNumber,
            long transactionEnergy,
            int stackDepth) {
        this.destinationAddress = destinationAddress;
        this.originAddress = originAddress;
        this.senderAddress = senderAddress;
        this.logs = logs;
        this.internalTransactions = internalTransactions;
        this.deletedAddresses = deletedAddresses;
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

    public List<Log> getLogs() {
        return this.logs;
    }

    public List<InternalTransactionInterface> getInternalTransactions() {
        return this.internalTransactions;
    }

    public List<AionAddress> getDeletedAddresses() {
        return this.deletedAddresses;
    }

    public void addInternalTransaction(InternalTransactionInterface internalTransaction) {
        this.internalTransactions.add(internalTransaction);
    }

    public void addLogs(List<Log> logs) {
        this.logs.addAll(logs);
    }

    public void addInternalTransactions(List<InternalTransactionInterface> internalTransactions) {
        this.internalTransactions.addAll(internalTransactions);
    }

    public void markAllInternalTransactionsAsRejected() {
        for (InternalTransactionInterface internalTransaction : this.internalTransactions) {
            internalTransaction.markAsRejected();
        }
    }
}
