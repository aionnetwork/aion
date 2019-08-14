package org.aion.precompiled.type;

import java.util.List;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;

public final class PrecompiledWrappedTransactionResult {
    public final TransactionResult result;
    public final List<AionAddress> deletedAddresses;

    /**
     * Constructs a new result wrapper that wraps the provided result and also contains additional
     * information such as the list of deleted addresses.
     *
     * @param result The result to wrap.
     * @param deletedAddresses The deleted addresses.
     */
    public PrecompiledWrappedTransactionResult(TransactionResult result, List<AionAddress> deletedAddresses) {
        if (result == null) {
            throw new NullPointerException("Cannot construct TransactionResult with null result!");
        }

        this.result = result;
        this.deletedAddresses = deletedAddresses;
    }

    public String toString() {
        return "PrecompiledWrappedTransactionResult { result = " + this.result + ", deleted addresses = " + this.deletedAddresses + " }";
    }
}
