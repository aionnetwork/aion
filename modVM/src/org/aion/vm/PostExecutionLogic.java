package org.aion.vm;

import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;

/**
 * A functional interface that specifies post-execution logic that is to be applied
 * immediately after a transaction has been executed. Or, if the transaction is being executed in
 * bulk, then it is to be applied in such a manner that it appears to be logically applied immediately
 * after the execution of the transaction so that from the perspective of the caller there is no
 * difference between running the transactions in bulk or sequentially.
 */
@FunctionalInterface
public interface PostExecutionLogic {

    /**
     * Performs the specified post-execution logic that is to be done immediately after a transaction
     * has been executed.
     *
     * <p>This logic may do whatever it needs to the provided inputs.
     *
     * @param repository The top-most level of the repository.
     * @param repositoryChild The child repository of repository.
     * @param summary The transaction execution summary.
     * @param transaction The transaction.
     */
    void apply(
            Repository repository,
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryChild,
            AionTxExecSummary summary,
            AionTransaction transaction);
}
