package org.aion.vm;

import java.util.Collections;
import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.zero.types.AionTxExecSummary;
import org.slf4j.Logger;
import org.aion.fastvm.TransactionExecutor;

/**
 * One day this will actually be in the proper shape!
 *
 * <p>This will be the executor that takes in bulk transactions and invokes the vm's as needed.
 *
 * <p>Getting this into the proper shape will be done slowly..
 */
public class BulkExecutor {
    private TransactionExecutor executor;

    public BulkExecutor(
            BlockDetails details,
            IRepository repo,
            boolean isLocalCall,
            long blockRemainingNrg,
            Logger logger) {

        executor =
                new TransactionExecutor(
                        details.getTransactions().get(0),
                        details.getBlock(),
                        repo,
                        isLocalCall,
                        blockRemainingNrg,
                        logger);
    }

    public List<AionTxExecSummary> execute() {
        return Collections.singletonList(this.executor.execute());
    }

    public void setBypassNonce() {
        this.executor.setBypassNonce();
    }
}
