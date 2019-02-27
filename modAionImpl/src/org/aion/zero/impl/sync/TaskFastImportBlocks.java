package org.aion.zero.impl.sync;

import java.util.List;
import org.aion.mcf.core.ImportResult;
import org.aion.types.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Task for importing blocks into the repository when running fast sync. The blocks are processed
 * different from {@link TaskImportBlocks} because fewer consensus validations are applied when
 * running fast sync. Also, the blocks are imported in reverse order starting from the oldest
 * ancestor back to the first block.
 *
 * @author Alexandra Roatis
 */
final class TaskFastImportBlocks implements Runnable {

    private final AionBlockchainImpl chain;
    private final FastSyncManager fastSyncMgr;
    private ByteArrayWrapper required;
    private final Logger log;

    TaskFastImportBlocks(
            final AionBlockchainImpl chain, final FastSyncManager fastSyncMgr, final Logger log) {
        this.chain = chain;
        this.fastSyncMgr = fastSyncMgr;
        this.log = log;
        this.required = null;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (!fastSyncMgr.isComplete()) {
            if (fastSyncMgr.isCompleteBlockData()) {
                // the block data is complete, but fast sync may still fail and reset pivot
                required = null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            } else {
                if (required == null) {
                    required = fastSyncMgr.getPivotHash();
                } else {
                    BlocksWrapper bw = fastSyncMgr.takeFilteredBlocks(required);

                    // the fastSyncMgr ensured the batch cannot be empty
                    List<AionBlock> batch = bw.getBlocks();

                    // process batch and update the peer state
                    ImportResult importResult;
                    AionBlock lastImported = null;

                    for (AionBlock b : batch) {
                        try {
                            long t1 = System.currentTimeMillis();
                            importResult = this.chain.tryFastImport(b);
                            long t2 = System.currentTimeMillis();
                            if (log.isDebugEnabled()) {
                                // printing sync mode only when debug is enabled
                                log.debug(
                                        "<import-status: node = {},  hash = {}, number = {}, txs = {}, block time = {}, result = {}, time elapsed = {} ms>",
                                        bw.getDisplayId(),
                                        b.getShortHash(),
                                        b.getNumber(),
                                        b.getTransactionsList().size(),
                                        b.getTimestamp(),
                                        importResult,
                                        t2 - t1);
                            } else {
                                // a different message will be printed to indicate the storage of
                                // blocks
                                if (log.isInfoEnabled()) {
                                    log.info(
                                            "<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                                            bw.getDisplayId(),
                                            b.getShortHash(),
                                            b.getNumber(),
                                            b.getTransactionsList().size(),
                                            importResult,
                                            t2 - t1);
                                }
                            }

                            if (importResult.isBest()) {
                                lastImported = b;
                            } else if (importResult == ImportResult.EXIST) {
                                lastImported = null; // to not update required incorrectly below
                                required = chain.findMissingAncestor(b.getParentHash());
                                break; // no need to continue importing
                            }
                        } catch (Exception e) {
                            log.error("<import-block throw> ", e);

                            if (e.getMessage() != null
                                    && e.getMessage().contains("No space left on device")) {
                                log.error("Shutdown due to lack of disk space.");
                                System.exit(0);
                            }
                            break;
                        }
                    }

                    // update the required hash
                    if (lastImported != null) {
                        required = ByteArrayWrapper.wrap(lastImported.getParentHash());
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Thread ["
                            + Thread.currentThread().getName()
                            + "] performing fast sync block imports was shutdown.");
        }
    }
}
