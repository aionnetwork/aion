package org.aion.vm;

import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;

/**
 * An abstract notion of work that is to be applied after a transaction has been executed.
 */
public final class PostExecutionWork {
    private final Repository repository;
    private final PostExecutionLogic postExecutionLogic;

    public PostExecutionWork(Repository repository, PostExecutionLogic logic) {
        if (logic == null) {
            throw new NullPointerException("Cannot create PostExecutionWork with null logic!");
        }
        this.repository = repository;
        this.postExecutionLogic = logic;
    }

    /**
     * Performs the work and returns the amount of energy remaining in the block after executing
     * the specified transaction.
     */
    public long doWork(RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache, AionTxExecSummary summary, AionTransaction transaction, long blockEnergyRemaining) {
        return this.postExecutionLogic.apply(this.repository, repositoryCache, summary, transaction, blockEnergyRemaining);
    }
}
