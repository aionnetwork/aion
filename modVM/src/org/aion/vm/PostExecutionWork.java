package org.aion.vm;

import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.base.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;

/** An abstract notion of work that is to be applied after a transaction has been executed. */
public final class PostExecutionWork {
    private final Repository repository;
    private final PostExecutionLogic postExecutionLogic;

    public PostExecutionWork(Repository repository, PostExecutionLogic logic) {
        if (repository == null) {
            throw new NullPointerException("Cannot create PostExecutionWork with null repository!");
        }
        if (logic == null) {
            throw new NullPointerException("Cannot create PostExecutionWork with null logic!");
        }
        this.repository = repository;
        this.postExecutionLogic = logic;
    }

    /** Performs some work. */
    public void doWork(
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache,
            AionTxExecSummary summary,
            AionTransaction transaction) {
        this.postExecutionLogic.apply(this.repository, repositoryCache, summary, transaction);
    }
}
