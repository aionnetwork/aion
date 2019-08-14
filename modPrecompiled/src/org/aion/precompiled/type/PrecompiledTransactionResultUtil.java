package org.aion.precompiled.type;

import java.util.ArrayList;
import java.util.List;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;

public final class PrecompiledTransactionResultUtil {

    public static TransactionStatus transactionStatusFromPrecompiledResultCode(PrecompiledResultCode code) {
        if (code.isSuccess()) {
            return TransactionStatus.successful();
        } else if (code.isRejected()) {
            return TransactionStatus.rejection(code.toString());
        } else if (code.isRevert()) {
            return TransactionStatus.revertedFailure();
        } else if (code.isFailed()) {
            return TransactionStatus.nonRevertedFailure(code.toString());
        } else {
            return TransactionStatus.fatal(code.toString());
        }
    }

    public static PrecompiledWrappedTransactionResult createPrecompiledWrappedTransactionResult(
        PrecompiledResultCode code,
        List<InternalTransaction> internalTransactions,
        List<Log> logs,
        long energyUsed,
        byte[] output,
        List<AionAddress> deletedAddresses) {

        TransactionStatus status = transactionStatusFromPrecompiledResultCode(code);
        TransactionResult result = new TransactionResult(status, logs, internalTransactions, energyUsed, output);

        return new PrecompiledWrappedTransactionResult(result, deletedAddresses);
    }

    public static PrecompiledWrappedTransactionResult createWithCodeAndEnergyRemaining(
        PrecompiledResultCode code,
        long energyUsed) {

        TransactionStatus status = transactionStatusFromPrecompiledResultCode(code);
        TransactionResult result = new TransactionResult(status, new ArrayList<>(), new ArrayList<>(), energyUsed, new byte[0]);

        return new PrecompiledWrappedTransactionResult(result, new ArrayList<>());
    }
}

