package org.aion.zero.impl.sync;

import java.util.List;
import java.util.stream.Collectors;
import org.aion.mcf.core.FastImportResult;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.types.AionBlock;
import org.apache.commons.lang3.tuple.Pair;
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
    private long requiredLevel;
    private ByteArrayWrapper requiredHash;
    private final Logger log;

    TaskFastImportBlocks(
            final AionBlockchainImpl chain, final FastSyncManager fastSyncMgr, final Logger log) {
        this.chain = chain;
        this.fastSyncMgr = fastSyncMgr;
        this.log = log;
        this.requiredLevel = 0;
        this.requiredHash = null;
    }

    @Override
    public void run() {
        // TODO: determine correct priority when full fast sync is added
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        while (!fastSyncMgr.isComplete()) {
            if (fastSyncMgr.isCompleteBlockData()) {
                // the block data is complete, but fast sync may still fail and reset pivot
                requiredLevel = 0;
                requiredHash = null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    if (!fastSyncMgr.isComplete()) {
                        log.error(
                                "Fast import blocks thread interrupted without shutdown request.",
                                e);
                    }
                    return;
                }
            } else {
                if (requiredLevel == 0 || requiredHash == null) {
                    AionBlock pivot = fastSyncMgr.getPivot();
                    if (pivot != null) {
                        requiredLevel = pivot.getNumber();
                        requiredHash = pivot.getHashWrapper();
                    } else {
                        // wait for pivot to be set
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            if (!fastSyncMgr.isComplete()) {
                                log.error(
                                        "Fast import blocks thread interrupted without shutdown request.",
                                        e);
                            }
                            return;
                        }
                    }
                } else {
                    BlocksWrapper bw = fastSyncMgr.takeFilteredBlocks(requiredHash, requiredLevel);
                    if (bw == null) {
                        continue;
                    }

                    // filter blocks just in case they don't start at the correct place
                    List<AionBlock> batch =
                            bw.getBlocks().stream()
                                    .filter(b -> b.getNumber() <= requiredLevel)
                                    .collect(Collectors.toList()); // TODO: probably remove

                    // process batch and update the peer state
                    FastImportResult importResult;
                    AionBlock lastImported = null;

                    for (AionBlock b : batch) {
                        if (b.getNumber() > requiredLevel) {
                            continue;
                        }
                        try {
                            long t1 = System.currentTimeMillis();
                            importResult = this.chain.tryFastImport(b);
                            long t2 = System.currentTimeMillis();
                            if (log.isInfoEnabled()) {
                                log.info(
                                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms, td = {}>",
                                        bw.getDisplayId(),
                                        b.getShortHash(),
                                        b.getNumber(),
                                        b.getTransactionsList().size(),
                                        importResult,
                                        t2 - t1,
                                        this.chain.getTotalDifficulty());
                            }

                            if (importResult.isSuccessful()) {
                                lastImported = b;
                                fastSyncMgr.addToImportedBlocks(b.getHashWrapper());
                            } else if (importResult.isKnown()) {
                                lastImported = null; // to not update required incorrectly below

                                Pair<ByteArrayWrapper, Long> pair = chain.findMissingAncestor(b);

                                if (pair != null) {
                                    requiredLevel = pair.getRight();
                                    requiredHash = pair.getLeft();
                                    // check the last one in the batch
                                    if (batch.get(batch.size() - 1).getNumber() > requiredLevel) {
                                        break; // no need to continue importing
                                    } else {
                                        continue;
                                    }
                                } else {
                                    // might be complete, exit current loop
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.error("<import-block throw> ", e);

                            if (e.getMessage() != null
                                    && e.getMessage().contains("No space left on device")) {
                                log.error("Shutdown due to lack of disk space.", e);
                                System.exit(SystemExitCodes.OUT_OF_DISK_SPACE);
                            }
                            break;
                        }
                    }

                    // update the required hash
                    if (lastImported != null) {
                        requiredLevel = lastImported.getNumber() - 1;
                        requiredHash = lastImported.getParentHashWrapper();
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
