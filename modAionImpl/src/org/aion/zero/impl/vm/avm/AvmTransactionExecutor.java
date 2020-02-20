package org.aion.zero.impl.vm.avm;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.aion.zero.impl.vm.avm.schedule.AvmVersionSchedule;
import org.aion.zero.impl.vm.common.PostExecutionWork;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.avm.stub.AvmExecutionType;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.stub.IAvmFutureResult;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.types.TransactionStatus;

/**
 * A class that is responsible for executing transactions using the AVM.
 *
 * This class is thread-safe.
 */
public final class AvmTransactionExecutor {

    /**
     * Executes the specified transactions using the avm, and returns the execution summaries of
     * each of the transactions.
     *
     * This method does not perform any checks on its inputs! It operates under the assumption that
     * the caller has ensured the input parameters are correct!
     *
     * In particular, every object supplied must be non-null with the exception of
     * {@code postExecutionWork}, which may be null. Also, we must have
     * {@code initialBlockEnergyLimit <= blockEnergyLimit}.
     *
     * @param repository The current state of the world.
     * @param blockDifficulty The block difficulty.
     * @param blockNumber The current block number.
     * @param blockTimestamp The block timestamp.
     * @param blockEnergyLimit The energy limit of the block.
     * @param miner The miner address.
     * @param transactions The transactions to execute.
     * @param postExecutionWork The post-execution work to be applied after executing the transactions.
     * @param decrementBlockEnergyLimit Whether or not to check the block energy limit.
     * @param allowNonceIncrement Whether to increment the sender's nonce or not.
     * @param isLocalCall Whether this is a local call (ie. is to cause no state changes).
     * @param remainingBlockEnergy The amount of energy remaining in the block.
     * @param executionType The avm execution type.
     * @param cachedBlockNumber The cached block number.
     * @return the execution summaries of the transactions.
     * @throws VmFatalException If a fatal error occurred and the kernel must be shut down.
     */
    public static List<AionTxExecSummary> executeTransactions(RepositoryCache<AccountState> repository, BigInteger blockDifficulty, long blockNumber, long blockTimestamp, long blockEnergyLimit, AionAddress miner, AionTransaction[] transactions, PostExecutionWork postExecutionWork, boolean decrementBlockEnergyLimit, boolean allowNonceIncrement, boolean isLocalCall, long remainingBlockEnergy, AvmExecutionType executionType, long cachedBlockNumber, boolean unityForkEnabled) throws VmFatalException {
        List<AionTxExecSummary> transactionSummaries = new ArrayList<>();
        long blockEnergy = remainingBlockEnergy;

        try {
            // We need to acquire the provider's lock before we can do anything meaningful.
            if (!AvmProvider.tryAcquireLock(10, TimeUnit.MINUTES)) {
                throw new TimeoutException("Timed out waiting to acquire the avm provider lock!");
            }

            // Ensure that the vm is in the correct state and grab the version of the avm we need to use for this block.
            AvmVersion versionToUse = updateAvmsAndGetVersionToUse(AvmConfigurations.getProjectRootDirectory(), blockNumber);

            IAvmFutureResult[] futures = invokeAvm(versionToUse, repository, blockDifficulty, blockNumber, blockTimestamp, blockEnergyLimit, miner, transactions, allowNonceIncrement, isLocalCall, executionType, cachedBlockNumber, unityForkEnabled);

            // Process the transaction results.
            int index = 0;
            for (IAvmFutureResult future : futures) {
                TransactionResult result = future.getResult();

                if (result.transactionStatus.isFatal()) {
                    throw new VmFatalException(result.transactionStatus.causeOfError);
                }

                // Check the block energy limit and reject if necessary.
                AionTransaction transaction = transactions[index];
                if (result.energyUsed > blockEnergy) {
                    result = markAsBlockEnergyLimitExceeded(result, transaction.getEnergyLimit());
                }

                AionTxExecSummary summary = buildSummaryAndUpdateState(future, transaction, result, versionToUse, repository, blockDifficulty, blockNumber, blockTimestamp, blockEnergyLimit, miner, allowNonceIncrement, isLocalCall);

                // Do any post execution work if any is specified.
                if (postExecutionWork != null) {
                    postExecutionWork.doWork(repository, summary, transaction);
                }

                // Update the remaining block energy.
                if (!result.transactionStatus.isRejected() && decrementBlockEnergyLimit) {
                    blockEnergy -= summary.getReceipt().getEnergyUsed();
                }

                transactionSummaries.add(summary);
                index++;
            }

        } catch (Throwable e) {
            // If we get here then something unexpected went wrong, we treat this as a fatal situation since shutting down is our only recovery.
            System.err.println("Encountered an unexpected error while processing the transactions in the avm: " + e.toString());
            throw new VmFatalException(e);
        } finally{
            AvmProvider.releaseLock();
        }

        return transactionSummaries;
    }

    /**
     * Updates the state of the avm versions depending on the current block number.
     *
     * This method will ensure that any avm versions that are enabled but which are prohibited to
     * be enabled at this block number will be shutdown and disabled.
     *
     * It will also ensure that the avm version that is considered the canonical version at this
     * block number is enabled and that its avm is started.
     *
     * @implNote The projectRootPath is the path to the root directory of the aion project. This is
     * required so that we can find the resources to load.
     *
     * @param projectRootPath The path of the project root directory.
     * @param currentBlockNumber The current block number.
     * @return the version of the avm to use for the given block number.
     */
    public static AvmVersion updateAvmsAndGetVersionToUse(String projectRootPath, long currentBlockNumber) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        AvmVersionSchedule schedule = AvmConfigurations.getAvmVersionSchedule();
        AvmVersion versionToUse = schedule.whichVersionToRunWith(currentBlockNumber);
        if (versionToUse == null) {
            throw new IllegalStateException("Attempted to invoke the avm at a block that has no avm support!");
        }

        if (versionToUse == AvmVersion.VERSION_1) {
            disableVersionIfEnabledAndProhibited(schedule, AvmVersion.VERSION_2, currentBlockNumber);
            ensureVersionIsEnabledAndStarted(AvmVersion.VERSION_1, projectRootPath);
        } else if (versionToUse == AvmVersion.VERSION_2) {
            disableVersionIfEnabledAndProhibited(schedule, AvmVersion.VERSION_1, currentBlockNumber);
            ensureVersionIsEnabledAndStarted(AvmVersion.VERSION_2, projectRootPath);
        } else {
            throw new IllegalStateException("Unknown avm version: " + versionToUse);
        }

        return versionToUse;
    }

    /**
     * Builds a transaction summary and updates the current state of the world. If the transction
     * was rejected, or if this is a local call, then no state changes will be made. Otherwise,
     * the world state will be updated.
     *
     * Returns the built summary.
     *
     * @param future The future result.
     * @param transaction The transaction that was run.
     * @param result The result of running the transaction.
     * @param avmVersion The version of the avm that was used.
     * @param repository The current state of the world.
     * @param blockDifficulty The block difficulty.
     * @param blockNumber The current block number.
     * @param blockTimestamp The block timestamp.
     * @param blockEnergyLimit The energy limit of the block.
     * @param miner The miner address.
     * @param allowNonceIncrement Whether to increment the sender's nonce or not.
     * @param isLocalCall Whether this is a local call (ie. is to cause no state changes).
     * @return the execution summary.
     */
    private static AionTxExecSummary buildSummaryAndUpdateState(IAvmFutureResult future, AionTransaction transaction, TransactionResult result, AvmVersion avmVersion, RepositoryCache<AccountState> repository, BigInteger blockDifficulty, long blockNumber, long blockTimestamp, long blockEnergyLimit, AionAddress miner, boolean allowNonceIncrement, boolean isLocalCall) {
        AionTxExecSummary summary = buildTransactionSummary(transaction, result);

        // Update the repository by committing any changes in the Avm so long as the transaction was not rejected.
        if (!result.transactionStatus.isRejected() && !isLocalCall) {
            // We have to build a new world state so things flush to repository.
            IAvmExternalState currentWorldState = AvmProvider.newExternalStateBuilder(avmVersion)
                .withRepository(repository)
                .withMiner(miner)
                .withDifficulty(blockDifficulty)
                .withBlockNumber(blockNumber)
                .withBlockTimestamp(blockTimestamp)
                .withBlockEnergyLimit(blockEnergyLimit)
                .withEnergyRules(AvmConfigurations.getEnergyLimitRules())
                .allowNonceIncrement(allowNonceIncrement)
                .isLocalCall(isLocalCall)
                .build();

            future.commitStateChangesTo(currentWorldState);
        }

        return summary;
    }

    /**
     * Invokes the avm, whichever is the specified versionToUse, to run the given transactions under
     * the given circumstances. Returns a list of future results pertaining to the transactions.
     *
     * @param versionToUse The version of the avm to use.
     * @param repository The current world state.
     * @param blockDifficulty The block difficulty.
     * @param blockNumber The current block number.
     * @param blockTimestamp The block timestamp.
     * @param blockEnergyLimit The energy limit of the block.
     * @param miner The miner address.
     * @param transactions The transactions to execute.
     * @param allowNonceIncrement Whether to increment the sender's nonce or not.
     * @param isLocalCall Whether this is a local call (ie. is to cause no state changes).
     * @param executionType The avm execution type.
     * @param cachedBlockNumber The cached block number.
     * @return the future execution results.
     */
    private static IAvmFutureResult[] invokeAvm(AvmVersion versionToUse, RepositoryCache<AccountState> repository, BigInteger blockDifficulty, long blockNumber, long blockTimestamp, long blockEnergyLimit, AionAddress miner, AionTransaction[] transactions, boolean allowNonceIncrement, boolean isLocalCall, AvmExecutionType executionType, long cachedBlockNumber, boolean unityForkEnabled) {
        IAvmExternalState externalState = AvmProvider.newExternalStateBuilder(versionToUse)
            .withRepository(repository)
            .withMiner(miner)
            .withDifficulty(blockDifficulty)
            .withBlockNumber(blockNumber)
            .withBlockTimestamp(blockTimestamp)
            .withBlockEnergyLimit(blockEnergyLimit)
            .withEnergyRules(unityForkEnabled ? AvmConfigurations.getEnergyLimitRulesAfterUnityFork() : AvmConfigurations.getEnergyLimitRules())
            .allowNonceIncrement(allowNonceIncrement)
            .isLocalCall(isLocalCall)
            .build();

        IAionVirtualMachine avm = AvmProvider.getAvm(versionToUse);
        return avm.run(externalState, toAionTypesTransactions(transactions), executionType, cachedBlockNumber);
    }

    /**
     * If the specified version is enabled but it is prohibited to be enabled at the current block
     * number, then that version will be shutdown and disabled. Otherwise this method does nothing.
     *
     * @param schedule The version schedule.
     * @param version The version to check.
     * @param currentBlockNumber The current block number.
     */
    private static void disableVersionIfEnabledAndProhibited(AvmVersionSchedule schedule, AvmVersion version, long currentBlockNumber) throws IOException {
        if ((AvmProvider.isVersionEnabled(version)) && (schedule.isVersionProhibitedAtBlockNumber(version, currentBlockNumber))) {
            AvmProvider.shutdownAvm(version);
            AvmProvider.disableAvmVersion(version);
        }
    }

    /**
     * Ensures that after this call the specified version of the avm will be enabled and the avm will
     * be started.
     *
     * @param version The version to make active.
     * @param projectRootPath The path of the project root directory.
     */
    private static void ensureVersionIsEnabledAndStarted(AvmVersion version, String projectRootPath) throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        if (!AvmProvider.isVersionEnabled(version)) {
            AvmProvider.enableAvmVersion(version, projectRootPath);
            AvmProvider.startAvm(version);
        } else if (!AvmProvider.isAvmRunning(version)) {
            AvmProvider.startAvm(version);
        }
    }

    /**
     * Converts the specified transactions to equivalent aion_types transactions.
     *
     * @param transactions The transactions to convert.
     * @return the converted transactions.
     */
    private static Transaction[] toAionTypesTransactions(AionTransaction[] transactions) {
        Transaction[] aionTypesTransactions = new Transaction[transactions.length];

        int index = 0;
        for (AionTransaction transaction : transactions) {
            aionTypesTransactions[index] = toAionTypesTransaction(transaction);
            index++;
        }

        return aionTypesTransactions;
    }

    /**
     * Converts the specified transaction to an equivalent aion_types transaction.
     *
     * @param transaction The transaction to convert.
     * @return the converted transaction.
     */
    private static Transaction toAionTypesTransaction(AionTransaction transaction) {
        if (transaction.isContractCreationTransaction()) {
            return Transaction.contractCreateTransaction(transaction.getSenderAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        } else {
            return Transaction.contractCallTransaction(transaction.getSenderAddress(), transaction.getDestinationAddress(), transaction.getTransactionHash(), transaction.getNonceBI(), new BigInteger(1, transaction.getValue()), transaction.getData(), transaction.getEnergyLimit(), transaction.getEnergyPrice());
        }
    }

    /**
     * Returns the original result with all fields set identically except that now the result is
     * rejected and the energy used is the specified energyUsed, and there is no output.
     *
     * @param original The original result.
     * @param energyUsed The new energy used.
     * @return the rejected version of the result.
     */
    private static TransactionResult markAsBlockEnergyLimitExceeded(TransactionResult original, long energyUsed) {
        return new TransactionResult(TransactionStatus.rejection("Rejected: block energy limit exceeded"), original.logs, original.internalTransactions, energyUsed, new byte[0]);
    }

    /**
     * Constructs a new execution summary for the given transaction & result pair.
     *
     * @param transaction The transaction.
     * @param result The transaction result.
     * @return the summary.
     */
    private static AionTxExecSummary buildTransactionSummary(AionTransaction transaction, TransactionResult result) {
        List<Log> logs = result.transactionStatus.isSuccess() ? result.logs : new ArrayList<>();
        byte[] output = result.copyOfTransactionOutput().orElse(new byte[0]);

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(makeReceipt(transaction, logs, result, output))
            .logs(logs)
            .deletedAccounts(new ArrayList<>())
            .internalTransactions(result.internalTransactions)
            .result(output);

        TransactionStatus resultCode = result.transactionStatus;
        if (resultCode.isRejected()) {
            builder.markAsRejected();
        } else if (resultCode.isFailed()) {
            builder.markAsFailed();
        }

        return builder.build();
    }

    /**
     * Constructs a new transaction receipt.
     *
     * @param transaction The transaction.
     * @param logs The logs fired off during execution of the transaction.
     * @param result The transaction result.
     * @param output The transaction output.
     * @return the receipt.
     */
    private static AionTxReceipt makeReceipt(AionTransaction transaction, List<Log> logs, TransactionResult result, byte[] output) {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setTransaction(transaction);
        receipt.setLogs(logs);
        receipt.setNrgUsed(result.energyUsed);
        receipt.setExecutionResult(output);
        receipt.setError(result.transactionStatus.causeOfError);
        return receipt;
    }
}
