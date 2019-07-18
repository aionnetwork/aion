package org.aion.precompiled.type;

import java.math.BigInteger;
import java.util.ArrayList;
import org.aion.base.AionTransaction;
import org.aion.base.TxUtil;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.ContractInfo;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.types.AionAddress;

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
            return new PrecompiledTransactionResult(PrecompiledResultCode.SUCCESS, energyRemaining);
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
    public static PrecompiledTransactionResult executeExternalCall(
            IExternalStateForPrecompiled externalState, AionTransaction transaction) {
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
                        PrecompiledResultCode.SUCCESS,
                        transaction.getEnergyLimit() - TxUtil.calculateTransactionCost(transaction));

        // Perform the rejection checks and return immediately if transaction is rejected.
        performRejectionChecks(childExternalState, transaction, result);
        if (!result.getResultCode().isSuccess()) {
            return result;
        }

        incrementNonceAndDeductEnergyCost(childExternalState, transaction);

        // Ensure that our caller did not erroneously pass us a CREATE transaction.
        if (transaction.isContractCreationTransaction()) {
            throw new IllegalStateException("A precompiled contract call cannot be a CREATE!");
        }

        result = runPrecompiledContractCall(grandChildExternalState, context, result, transaction);

        // If the execution was successful then we can safely commit any changes in the grandChild
        // up to the child kernel.
        if (result.getResultCode().isSuccess()) {
            grandChildExternalState.commit();
        }

        // If the execution was not rejected then we can safely commit any changes in the child
        // kernel up to its parent.
        if (!result.getResultCode().isRejected()) {
            childExternalState.commit();
        }

        // Propagate any side-effects.
        result.addLogs(context.getLogs());
        result.addInternalTransactions(context.getInternalTransactions());
        result.addDeletedAddresses(context.getDeletedAddresses());

        return result;
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
    public static PrecompiledTransactionResult runPrecompiledContractCall(
            IExternalStateForPrecompiled externalState,
            PrecompiledTransactionContext context,
            PrecompiledTransactionResult result,
            AionTransaction transaction) {

        ContractFactory precompiledFactory = new ContractFactory();
        PrecompiledContract precompiledContract =
                precompiledFactory.getPrecompiledContract(context, externalState);

        // Ensure we actually have a precompiled contract as our destination.
        if (!ContractInfo.isPrecompiledContract(transaction.getDestinationAddress())) {
            throw new IllegalStateException("Expected destination to be a precompiled contract!");
        }

        // Execute the call. Note the contract may be null if the sender does not have the correct
        // call permissions for the contract!
        PrecompiledTransactionResult newResult = null;
        if (precompiledContract != null) {
            newResult =
                    precompiledContract.execute(transaction.getData(), context.transactionEnergy);
        }

        // Transfer any specified value from the sender to the recipient.
        BigInteger transferValue = new BigInteger(1, transaction.getValue());
        externalState.addBalance(transaction.getSenderAddress(), transferValue.negate());
        externalState.addBalance(transaction.getDestinationAddress(), transferValue);

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
     * @return the rejection-check result.
     */
    public static void performRejectionChecks(
            IExternalStateForPrecompiled externalState,
            AionTransaction transaction,
            PrecompiledTransactionResult result) {
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        long energyLimit = transaction.getEnergyLimit();

        if (transaction.isContractCreationTransaction()) {
            if (!externalState.isValidEnergyLimitForCreate(energyLimit)) {
                result.setResultCode(PrecompiledResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        } else {
            if (!externalState.isValidEnergyLimitForNonCreate(energyLimit)) {
                result.setResultCode(PrecompiledResultCode.INVALID_NRG_LIMIT);
                result.setEnergyRemaining(energyLimit);
                return;
            }
        }

        BigInteger txNonce = new BigInteger(1, transaction.getNonce());
        if (!externalState.accountNonceEquals(transaction.getSenderAddress(), txNonce)) {
            result.setResultCode(PrecompiledResultCode.INVALID_NONCE);
            result.setEnergyRemaining(0);
            return;
        }

        BigInteger transferValue = transaction.getValueBI();
        BigInteger transactionCost =
                energyPrice.multiply(BigInteger.valueOf(energyLimit)).add(transferValue);
        if (!externalState.accountBalanceIsAtLeast(transaction.getSenderAddress(), transactionCost)) {
            result.setResultCode(PrecompiledResultCode.INSUFFICIENT_BALANCE);
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
    public static void incrementNonceAndDeductEnergyCost(
            IExternalStateForPrecompiled externalState, AionTransaction transaction) {
        IExternalStateForPrecompiled childExternalState = externalState.newChildExternalState();
        childExternalState.incrementNonce(transaction.getSenderAddress());
        BigInteger energyLimit = BigInteger.valueOf(transaction.getEnergyLimit());
        BigInteger energyPrice = BigInteger.valueOf(transaction.getEnergyPrice());
        BigInteger energyCost = energyLimit.multiply(energyPrice);
        childExternalState.deductEnergyCost(transaction.getSenderAddress(), energyCost);
        childExternalState.commit();
    }

    private static PrecompiledTransactionContext constructTransactionContext(
            AionTransaction transaction, IExternalStateForPrecompiled externalState) {
        AionAddress originAddress = transaction.getSenderAddress();
        AionAddress callerAddress = transaction.getSenderAddress();
        byte[] transactionHash = transaction.getTransactionHash();
        long blockNumber = externalState.getBlockNumber();
        long energyRemaining = transaction.getEnergyLimit() - TxUtil.calculateTransactionCost(transaction);
        AionAddress destinationAddress =
                transaction.isContractCreationTransaction()
                        ? TxUtil.calculateContractAddress(transaction)
                        : transaction.getDestinationAddress();

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
