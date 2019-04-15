package org.aion.mcf.valid;

import static org.aion.mcf.tx.TransactionTypes.AVM;
import static org.aion.mcf.tx.TransactionTypes.AVM_CREATE_CODE;
import static org.aion.mcf.tx.TransactionTypes.CREATE;
import static org.aion.mcf.tx.TransactionTypes.FVM;

import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.tx.TransactionTypes;

/**
 * Rules for validating transactions based on allowed types.
 *
 * @author Alexandra Roatis
 */
public class TransactionTypeRule {

    // allowing only balance transfers on AVM when this flag is equal to false.
    private static boolean AVM_CONTRACT_TRANSACTION_ALLOWED = false;

    /**
     * Validates the transaction type as follows:
     *
     * <ol>
     *   <li>Any transaction type is allowed before the 0.4 hard fork which enables the use of the
     *       AVM.
     *   <li>Only the transaction types listed in {@link org.aion.mcf.tx.TransactionTypes#ALL} are
     *       valid after the fork.
     *   <li>Contract deployments must have the transaction types from the set {@link
     *       org.aion.mcf.tx.TransactionTypes#CREATE}.
     *   <li>Transactions that are not contract deployments must have the transaction type {@link
     *       org.aion.mcf.tx.TransactionTypes#DEFAULT}
     * </ol>
     *
     * @param transaction the transaction to be validated
     * @return {@code true} is the transaction satisfies the rule described above; {@code false}
     *     otherwise
     */
    public static boolean isValidTransactionType(Transaction transaction) {
        if (AVM_CONTRACT_TRANSACTION_ALLOWED) {
            // transaction types are validated after the fork
            if (transaction.getDestinationAddress() == null) {
                // checks for valid contract deployments
                return CREATE.contains(transaction.getTargetVM());
            } else {
                // other transactions must have default type
                return transaction.getTargetVM() == TransactionTypes.DEFAULT;
            }
        } else {
            // transaction types are not checked before the fork
            return true;
        }
    }

    /**
     * Compares the given transaction type with all the transaction types allowed by the FastVM.
     *
     * @param type the type of a transaction applicable on the FastVM
     * @return {@code true} is this is an FastVM transaction, {@code false} otherwise
     */
    public static boolean isValidFVMCode(byte type) {
        return FVM.contains(type);
    }

    /**
     * Compares the given transaction type with all the transaction types allowed by the AVM.
     *
     * @param type the type of a transaction applicable on the AVM
     * @return {@code true} is this is an AVM transaction, {@code false} otherwise
     */
    public static boolean isValidAVMCode(byte type) {
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

    /** It should be triggered by 0.4 hard fork or testing purpose */
    public static void allowAVMContractTransaction() {
        AVM_CONTRACT_TRANSACTION_ALLOWED = true;
    }

    /** It should be triggered by 0.4 hard fork or testing purpose */
    public static void disallowAVMContractTransaction() {
        AVM_CONTRACT_TRANSACTION_ALLOWED = false;
    }
}
