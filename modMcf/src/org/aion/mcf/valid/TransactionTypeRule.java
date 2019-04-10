package org.aion.mcf.valid;

import static org.aion.mcf.tx.TransactionTypes.ALL;
import static org.aion.mcf.tx.TransactionTypes.AVM;
import static org.aion.mcf.tx.TransactionTypes.AVM_CREATE_CODE;
import static org.aion.mcf.tx.TransactionTypes.FVM;
import static org.aion.mcf.tx.TransactionTypes.FVM_CREATE_CODE;

/**
 * Rules for validating transactions based on allowed types.
 *
 * @author Alexandra Roatis
 */
public class TransactionTypeRule {

    // allowing only balance transfers on AVM when this flag is equal to false.
    private static boolean AVM_CONTRACT_TRANSACTION_ALLOWED = false;

    /**
     * Compares the given transaction type with all the transaction types allowed.
     *
     * @param type the type of a transaction applicable on any of the allowed VMs
     * @return {@code true} is this is a valid transaction type, {@code false} otherwise
     */
    public static boolean isValidTransactionType(byte type) {
        return ALL.contains(type);
    }

    /**
     * Compares the given transaction type with all the transaction types allowed by the FastVM.
     *
     * @param type the type of a transaction applicable on the FastVM
     * @return {@code true} is this is an FastVM transaction, {@code false} otherwise
     */
    public static boolean isValidFVMTransaction(byte type) {
        return FVM.contains(type);
    }

    /**
     * Checks if the given transaction is a valid contract deployment on the FastVM.
     *
     * @param type the type of a contract creation transaction
     * @return {@code true} is this is a valid FastVM contract deployment, {@code false} otherwise
     */
    public static boolean isValidFVMContractDeployment(byte type) {
        return !AVM_CONTRACT_TRANSACTION_ALLOWED || type == FVM_CREATE_CODE; // anything is valid here before the fork
    }

    /**
     * Compares the given transaction type with all the transaction types allowed by the AVM.
     *
     * @param type the type of a transaction applicable on the AVM
     * @return {@code true} is this is an AVM transaction, {@code false} otherwise
     */
    public static boolean isValidAVMTransaction(byte type) {
        return AVM.contains(type);
    }

    /**
     * Checks if the given transaction is a valid contract deployment on the AVM.
     *
     * @param type the type of a contract creation transaction
     * @return {@code true} is this is a valid AVM contract deployment, {@code false} otherwise
     */
    public static boolean isValidAVMContractDeployment(byte type) {
        return type == AVM_CREATE_CODE && AVM_CONTRACT_TRANSACTION_ALLOWED;
    }

    /**
     * It should be triggered by 0.4 hardfork or testing purpose
     */
    public static void allowAVMContractTransaction() {
        AVM_CONTRACT_TRANSACTION_ALLOWED = true;
    }
}
