package org.aion.precompiled.type;

import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.TxUtil;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.types.TransactionStatus;

public final class ContractExecutor {

    /**
     * Returns the result of executing the internal transaction whose context is the specified
     * context.
     *
     * This method only executes the input and does nothing else. This should only ever be called
     * by internal transactions!
     *
     * @param worldState The current state of the world.
     * @param context The transaction context.
     * @param input The call input.
     * @param energyRemaining The current energy remaining.
     * @return the execution result.
     */
    public static PrecompiledTransactionResult executeInternalCall(IExternalStateForPrecompiled worldState, PrecompiledTransactionContext context, byte[] input, long energyRemaining) {
        ContractFactory factory = new ContractFactory();
        PrecompiledContract precompiledContract = factory.getPrecompiledContract(context, worldState);

        if (precompiledContract == null) {
            return new PrecompiledTransactionResult(TransactionStatus.successful(), energyRemaining);
        } else {
            return precompiledContract.execute(input, energyRemaining);
        }
    }

    /**
     * Returns the result of executing the specified transaction, which is a transaction that calls
     * into a precompiled contract.
     *
     * This method performs verifications, balance transfers, etc. and runs as an external
     * transaction!
     *
     * @param externalState The current state of the world.
     * @param transaction The transaction.
     * @return the execution result.
     */
    public static PrecompiledWrappedTransactionResult executeExternalCall(
            IExternalStateForPrecompiled externalState, Transaction transaction) {
        if (externalState == null) {
            throw new NullPointerException("Cannot run using a null externalState!");
        }
        if (transaction == null) {
            throw new NullPointerException("Cannot run null transaction!");
        }

        PrecompiledTransactionContext context = constructTransactionContext(transaction, externalState);
        IExternalStateForPrecompiled childExternalState = externalState.newChildExternalState();
        IExternalStateForPrecompiled grandChildExternalState = childExternalState.newChildExternalState();

        PrecompiledTransactionResult result =
                new PrecompiledTransactionResult(
                        TransactionStatus.successful(),
                        transaction.energyLimit - TxUtil.calculateTransactionCost(transaction.copyOfTransactionData(), transaction.isCreate));

        // Perform the rejection checks and return immediately if transaction is rejected.
        performRejectionChecks(childExternalState, transaction, result);
        if (!result.getStatus().isSuccess()) {
            return PrecompiledTransactionResultUtil.createWithCodeAndEnergyRemaining(
                result.getStatus(),
                transaction.energyLimit - result.getEnergyRemaining());
        }

        incrementNonceAndDeductEnergyCost(childExternalState, transaction);

        // Ensure that our caller did not erroneously pass us a CREATE transaction.
        if (transaction.isCreate) {
            throw new IllegalStateException("A precompiled contract call cannot be a CREATE!");
        }

        result = runPrecompiledContractCall(grandChildExternalState, context, result, transaction);

        // If the execution was successful then we can safely commit any changes in the grandChild
        // up to the child kernel.
        if (result.getStatus().isSuccess()) {
            grandChildExternalState.commit();
        }

        // If the execution was not rejected then we can safely commit any changes in the child
        // kernel up to its parent.
        if (!result.getStatus().isRejected()) {
            childExternalState.commit();
        }

        return PrecompiledTransactionResultUtil.createPrecompiledWrappedTransactionResult(
            result.getStatus(),
            context.getInternalTransactions(),
            context.getLogs(),
            transaction.energyLimit - result.getEnergyRemaining(),
            result.getReturnData(),
            context.getDeletedAddresses());
    }

    /**
     * Returns the result of executing the transaction whose context is given by the specified
     * context, which is a precompiled contract call.
     *
     * <p>This method does not commit any changes in the provided kernel, it is the responsibility
     * of the caller to evaluate the returned result and determine how to proceed with the state
     * changes.
     *
     * @param externalState The current state of the world.
     * @param context The transaction context.
     * @param result The current state of the transaction result.
     * @param transaction The transaction.
     * @return the result of executing the transaction.
     */
    private static PrecompiledTransactionResult runPrecompiledContractCall(
            IExternalStateForPrecompiled externalState,
            PrecompiledTransactionContext context,
            PrecompiledTransactionResult result,
            Transaction transaction) {

        ContractFactory precompiledFactory = new ContractFactory();
        PrecompiledContract precompiledContract =
                precompiledFactory.getPrecompiledContract(context, externalState);

        // Ensure we actually have a precompiled contract as our destination.
        if (!ContractInfo.isPrecompiledContract(transaction.destinationAddress)) {
            throw new IllegalStateException("Expected destination to be a precompiled contract!");
        }

        // Execute the call. Note the contract may be null if the sender does not have the correct
        // call permissions for the contract!
        PrecompiledTransactionResult newResult = null;
        if (precompiledContract != null) {
            newResult =
                    precompiledContract.execute(transaction.copyOfTransactionData(), context.transactionEnergy);
        }

        // Transfer any specified value from the sender to the recipient.
        externalState.addBalance(transaction.senderAddress, transaction.value.negate());
        externalState.addBalance(transaction.destinationAddress, transaction.value);

        return (newResult == null) ? result : newResult;
    }

    /**
     * Returns a SUCCESS result only if the specified transaction is not to be rejected.
     *
     * <p>Otherwise, returns a REJECTED result with the appropriate error cause specified.
     *
     * @param externalState The state of the world.
     * @param transaction The transaction to verify.
     * @param result The current state of the transaction result.
     */
    private static void performRejectionChecks(
        IExternalStateForPrecompiled externalState,
        Transaction transaction,
        PrecompiledTransactionResult result) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.energyPrice);
        long energyLimit = transaction.energyLimit;

        if (!externalState.isValidEnergyLimitForNonCreate(energyLimit)) {
            result.setResultCode(TransactionStatus.rejection("INVALID_NRG_LIMIT"));
            result.setEnergyRemaining(energyLimit);
            return;
        }

        if (!externalState.accountNonceEquals(transaction.senderAddress, transaction.nonce)) {
            result.setResultCode(TransactionStatus.rejection("INVALID_NONCE"));
            result.setEnergyRemaining(0);
            return;
        }

        BigInteger transactionCost =
                energyPrice.multiply(BigInteger.valueOf(energyLimit)).add(transaction.value);
        if (!externalState.accountBalanceIsAtLeast(transaction.senderAddress, transactionCost)) {
            result.setResultCode(TransactionStatus.rejection("INSUFFICIENT_BALANCE"));
            result.setEnergyRemaining(0);
        }
    }

    /**
     * Increments the nonce of the sender of the transaction and deducts the energy cost from the
     * sender's account as well. The energy cost is equal to the energy limit multiplied by the
     * energy price.
     *
     * <p>These state changes are made directly in the given kernel.
     *
     * @param externalState The state of the world.
     * @param transaction The transaction.
     */
    private static void incrementNonceAndDeductEnergyCost(
            IExternalStateForPrecompiled externalState, Transaction transaction) {
        IExternalStateForPrecompiled childExternalState = externalState.newChildExternalState();
        childExternalState.incrementNonce(transaction.senderAddress);
        BigInteger energyLimit = BigInteger.valueOf(transaction.energyLimit);
        BigInteger energyPrice = BigInteger.valueOf(transaction.energyPrice);
        BigInteger energyCost = energyLimit.multiply(energyPrice);
        childExternalState.deductEnergyCost(transaction.senderAddress, energyCost);
        childExternalState.commit();
    }

    private static PrecompiledTransactionContext constructTransactionContext(
            Transaction transaction, IExternalStateForPrecompiled externalState) {
        AionAddress originAddress = transaction.senderAddress;
        AionAddress callerAddress = transaction.senderAddress;
        byte[] transactionHash = transaction.copyOfTransactionHash();
        long blockNumber = externalState.getBlockNumber();
        long energyRemaining = transaction.energyLimit - TxUtil.calculateTransactionCost(transaction.copyOfTransactionData(), transaction.isCreate);
        AionAddress destinationAddress = transaction.destinationAddress;

        return new PrecompiledTransactionContext(
                destinationAddress,
                originAddress,
                callerAddress,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                transactionHash,
                transactionHash,
                blockNumber,
                energyRemaining,
                0);
    }
}
