package org.aion.mcf.valid;

import org.aion.interfaces.vm.VirtualMachineSpecs;

/**
 * Rules for validating transactions based on allowed types.
 *
 * @author Alexandra Roatis
 */
public class TransactionTypeRule {

    /**
     * Compares the given transaction type with all the transaction types allowed by the FastVM.
     *
     * @param type the type of a contract creation transaction
     * @return {@code true} is this is a FastVM transaction, {@code false} otherwise
     */
    public static boolean isValidFVMTransactionType(byte type) {
        return type == VirtualMachineSpecs.FVM_CREATE_CODE;
    }

    /**
     * Compares the given transaction type with all the transaction types allowed by the AVM.
     *
     * @param type the type of a contract creation transaction
     * @return {@code true} is this is an AVM transaction, {@code false} otherwise
     */
    public static boolean isValidAVMTransactionType(byte type) {
        return type == VirtualMachineSpecs.AVM_CREATE_CODE;
    }
}
