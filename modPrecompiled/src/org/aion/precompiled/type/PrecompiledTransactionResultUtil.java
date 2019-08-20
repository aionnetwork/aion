package org.aion.precompiled.type;

import java.util.ArrayList;
import java.util.List;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;

public final class PrecompiledTransactionResultUtil {

    public static PrecompiledWrappedTransactionResult createPrecompiledWrappedTransactionResult(
        TransactionStatus status,
        List<InternalTransaction> internalTransactions,
        List<Log> logs,
        long energyUsed,
        byte[] output,
        List<AionAddress> deletedAddresses) {

        TransactionResult result = new TransactionResult(status, logs, internalTransactions, energyUsed, output);

        return new PrecompiledWrappedTransactionResult(result, deletedAddresses);
    }

    public static PrecompiledWrappedTransactionResult createWithCodeAndEnergyRemaining(
        TransactionStatus status,
        long energyUsed) {

        TransactionResult result = new TransactionResult(status, new ArrayList<>(), new ArrayList<>(), energyUsed, new byte[0]);

        return new PrecompiledWrappedTransactionResult(result, new ArrayList<>());
    }
}

