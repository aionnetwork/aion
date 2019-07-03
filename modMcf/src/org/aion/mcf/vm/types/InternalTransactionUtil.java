package org.aion.mcf.vm.types;

import java.util.ArrayList;
import java.util.List;
import org.aion.types.InternalTransaction;

public class InternalTransactionUtil {

    /**
     * Method that creates an identical copy of the original InternalTransaction except it is marked
     * as REJECTED
     *
     * @param original The Internal Transaction we were given.
     * @return The new Rejected Transaction instance.
     */
    public static InternalTransaction createRejectedTransaction(InternalTransaction original) {
        if (original.isCreate) {
            return InternalTransaction.contractCreateTransaction(
                    InternalTransaction.RejectedStatus.REJECTED,
                    original.sender,
                    original.senderNonce,
                    original.value,
                    original.copyOfData(),
                    original.energyLimit,
                    original.energyPrice);
        } else {
            return InternalTransaction.contractCallTransaction(
                    InternalTransaction.RejectedStatus.REJECTED,
                    original.sender,
                    original.destination,
                    original.senderNonce,
                    original.value,
                    original.copyOfData(),
                    original.energyLimit,
                    original.energyPrice);
        }
    }

    public static List<InternalTransaction> createRejectedTransactionList(
            List<InternalTransaction> transactions) {
        List<InternalTransaction> rejectedInternalTransactions = new ArrayList<>();
        for (InternalTransaction transaction : transactions) {
            rejectedInternalTransactions.add(
                    InternalTransactionUtil.createRejectedTransaction(transaction));
        }
        return rejectedInternalTransactions;
    }
}
