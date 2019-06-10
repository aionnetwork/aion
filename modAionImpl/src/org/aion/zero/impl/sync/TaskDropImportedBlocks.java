package org.aion.zero.impl.sync;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Deletes a batch of blocks that was already imported from storage.
 *
 * @author Alexandra Roatis
 */
final class TaskDropImportedBlocks implements Runnable {

    private final AionBlockchainImpl chain;
    private final long level;
    private final List<ByteArrayWrapper> importedQueues;
    private final Map<ByteArrayWrapper, List<AionBlock>> levelFromDisk;

    private final Logger log;

    TaskDropImportedBlocks(
            final AionBlockchainImpl _chain,
            final long _level,
            final List<ByteArrayWrapper> _importedQueues,
            final Map<ByteArrayWrapper, List<AionBlock>> _levelFromDisk,
            final Logger _log) {
        this.chain = _chain;
        this.level = _level;
        this.importedQueues = _importedQueues;
        this.levelFromDisk = _levelFromDisk;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);
        Thread.currentThread().setName("sync-drop:" + level);

        chain.dropImported(level, importedQueues, levelFromDisk);

        // log operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Dropped from storage level = {} with queues = {}.",
                    level,
                    Arrays.toString(importedQueues.toArray()));
        }
    }
}
