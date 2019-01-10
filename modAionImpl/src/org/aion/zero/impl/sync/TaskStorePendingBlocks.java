package org.aion.zero.impl.sync;

import java.util.List;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Places a batch of blocks into storage for later imports.
 *
 * @author Alexandra Roatis
 */
final class TaskStorePendingBlocks implements Runnable {

    private final AionBlockchainImpl chain;
    private final List<AionBlock> batch;
    private final String displayId;

    private final Logger log;

    TaskStorePendingBlocks(
            final AionBlockchainImpl _chain,
            final List<AionBlock> _batch,
            final String _displayId,
            final Logger _log) {
        this.chain = _chain;
        this.batch = _batch;
        this.displayId = _displayId;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);
        AionBlock first = batch.get(0);
        Thread.currentThread().setName("sync-save:" + first.getNumber());

        int stored = chain.storePendingBlockRange(batch);

        // log operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Stored {} out of {} blocks starting at hash = {}, number = {} from node = {}.",
                    stored,
                    batch.size(),
                    first.getShortHash(),
                    first.getNumber(),
                    displayId);
        }
    }
}
