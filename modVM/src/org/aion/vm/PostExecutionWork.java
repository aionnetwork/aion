package org.aion.vm;

import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;

/**
 * A functional interface that specifies post-execution "work" or logic that is to be applied
 * immediately after a transaction has been executed. Or, if the transaction is being executed in
 * bulk, then it is to be applied in such a manner that it appears to be logically applied immediately
 * after the execution of the transaction so that from the perspective of the caller there is no
 * difference between running the transactions in bulk or sequentially.
 */
@FunctionalInterface
public interface PostExecutionWork {

    /**
     * Performs the specified post-execution work that is to be done immediately after a transaction
     * has been executed.
     *
     * <p>This work may do whatever it needs to the provided inputs.
     *
     * <p>The work must return the amount of energy that this transaction will use in its block so
     * that the remaining block energy may be updated correctly.
     *
     * Note that this value may not always be relevant to the component of the kernel that wants
     * this work done, in which case that component should define this method to return zero (0) so
     * that no block energy usage deductions are made.
     *
     * @param repository The top-most level of the repository.
     * @param repositoryChild The child repository of repository.
     * @param summary The transaction execution summary.
     * @param transaction The transaction.
     * @param blockEnergyRemaining The amount of energy remaining in the block <b>prior</b> to
     *     execution.
     * @return The amount of energy that this transaction uses in its block.
     */
    long doPostExecutionWork(
            IRepository repository,
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild,
            AionTxExecSummary summary,
            AionTransaction transaction,
            long blockEnergyRemaining);
}