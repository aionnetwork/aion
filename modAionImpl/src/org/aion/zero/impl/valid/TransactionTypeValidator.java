package org.aion.zero.impl.valid;

import static org.aion.mcf.valid.TransactionTypeRule.isValidTransactionType;

import org.aion.interfaces.tx.Transaction;
import org.aion.zero.types.AionTransaction;

/**
 * Validator for the type field of transactions allowed by the network. The transaction types
 * currently correlate with which virtual machines are enabled.
 *
 * @author Alexandra Roatis
 */
public class TransactionTypeValidator {

    /**
     * Validates the transaction type as follows:
     *
     * <ol>
     *   <li>Any transaction type is allowed before the 0.4 hard fork which enables the use of the
     *       AVM.
     *   <li>Only the transaction types listed in {@link org.aion.mcf.tx.TransactionTypes#ALL} are
     *       valid after the fork.
     *   <li>Contract deployments must have the transaction types from the set {@link
     *       org.aion.mcf.tx.TransactionTypes#ALL}.
     *   <li>Transactions that are not contract deployments must have the transaction type {@link
     *       org.aion.mcf.tx.TransactionTypes#DEFAULT}
     * </ol>
     *
     * @param transaction the transaction to be validated
     * @return {@code true} is the transaction satisfies the rule described above; {@code false}
     *     otherwise
     * @implNote Delegates the check to {@link
     *     org.aion.mcf.valid.TransactionTypeRule#isValidTransactionType(Transaction)}.
     */
    public static boolean isValid(AionTransaction transaction) {
        /*
        TODO: this class could be replaced by org.aion.mcf.valid.TransactionTypeRule
        when the specification of modMcf and modAionImpl are properly defined and refactored
        */
        return isValidTransactionType(transaction);
    }
}
