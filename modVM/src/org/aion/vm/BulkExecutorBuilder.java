package org.aion.vm;

import java.util.List;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * A convenience class for building instances of {@link BulkExecutor}.
 */
public final class BulkExecutorBuilder {
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository = null;
    private PostExecutionWork postExecutionWork = null;
    private IAionBlock block = null;
    private List<AionTransaction> transactions = null;
    private Logger logger = null;
    private Boolean isLocalCall = null;
    private Boolean allowNonceIncrement = null;
    private Boolean fork040enable = null;
    private Boolean checkBlockEnergyLimit = null;

    public BulkExecutorBuilder repository(RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repository) {
        this.repository = repository;
        return this;
    }

    public BulkExecutorBuilder postExecutionWork(PostExecutionWork postExecutionWork) {
        this.postExecutionWork = postExecutionWork;
        return this;
    }

    public BulkExecutorBuilder blockToExecute(IAionBlock block) {
        this.block = block;
        return this;
    }

    public BulkExecutorBuilder transactionsToExecute(IAionBlock block, List<AionTransaction> transactions) {
        this.block = block;
        this.transactions = transactions;
        return this;
    }

    public BulkExecutorBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public BulkExecutorBuilder isLocalCall(boolean isLocalCall) {
        this.isLocalCall = isLocalCall;
        return this;
    }

    public BulkExecutorBuilder allowNonceIncrement(boolean allowNonceIncrement) {
        this.allowNonceIncrement = allowNonceIncrement;
        return this;
    }

    public BulkExecutorBuilder isFork040enabled(boolean isFork040enabled) {
        this.fork040enable = isFork040enabled;
        return this;
    }

    public BulkExecutorBuilder checkBlockEnergyLimit(boolean checkBlockEnergyLimit) {
        this.checkBlockEnergyLimit = checkBlockEnergyLimit;
        return this;
    }

    public BulkExecutor build() {
        if (this.repository == null) {
            throw new NullPointerException("Cannot build BulkExecutor with null repository!");
        }
        if (this.block == null) {
            throw new NullPointerException("Cannot build BulkExecutor with null block!");
        }
        if (this.logger == null) {
            throw new NullPointerException("Cannot build BulkExecutor with null logger!");
        }
        if (this.isLocalCall == null) {
            throw new NullPointerException("Cannot build BulkExecutor without specifying isLocalCall!");
        }
        if (this.allowNonceIncrement == null) {
            throw new NullPointerException("Cannot build BulkExecutor without specifying allowNonceIncrement!");
        }
        if (this.fork040enable == null) {
            throw new NullPointerException("Cannot build BulkExecutor without specifying fork040enable!");
        }
        if (this.checkBlockEnergyLimit == null) {
            throw new NullPointerException("Cannot build BulkExecutor without specifying checkBlockEnergyLimit!");
        }

        if (this.transactions != null) {
            // This means that only a subset of the transactions in the block are to be run.
            if (this.postExecutionWork == null) {
                return BulkExecutor.newExecutorWithNoPostExecutionWork(
                    this.block,
                    this.transactions,
                    this.repository,
                    this.isLocalCall,
                    this.allowNonceIncrement,
                    this.block.getNrgLimit(),
                    this.fork040enable,
                    this.checkBlockEnergyLimit,
                    this.logger);
            } else {
                return BulkExecutor.newExecutor(
                    this.block,
                    this.transactions,
                    this.repository,
                    this.isLocalCall,
                    this.allowNonceIncrement,
                    this.block.getNrgLimit(),
                    this.fork040enable,
                    this.checkBlockEnergyLimit,
                    this.logger,
                    this.postExecutionWork);
            }
        } else {
            // This means that every transaction in the block is to be run.
            if (this.postExecutionWork == null) {
                return BulkExecutor.newExecutorForBlockWithNoPostExecutionWork(
                    this.block,
                    this.repository,
                    this.isLocalCall,
                    this.allowNonceIncrement,
                    this.block.getNrgLimit(),
                    this.fork040enable,
                    this.checkBlockEnergyLimit,
                    this.logger);
            } else {
                return BulkExecutor.newExecutorForBlock(
                    this.block,
                    this.repository,
                    this.isLocalCall,
                    this.allowNonceIncrement,
                    this.block.getNrgLimit(),
                    this.fork040enable,
                    this.checkBlockEnergyLimit,
                    this.logger,
                    this.postExecutionWork);
            }
        }
    }
}
