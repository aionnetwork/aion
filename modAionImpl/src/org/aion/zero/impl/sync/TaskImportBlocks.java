package org.aion.zero.impl.sync;

import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.BACKWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.FORWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.NORMAL;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.slf4j.Logger;

/**
 * handle process of importing blocks to repo
 *
 * <p>TODO: targeted send
 *
 * @author chris
 */
final class TaskImportBlocks implements Runnable {

    private final AionBlockchainImpl chain;

    private final AtomicBoolean start;

    private final PriorityBlockingQueue<BlocksWrapper> sortedBlocks;

    private final SyncStats syncStats;

    private final Map<ByteArrayWrapper, Object> importedBlockHashes;

    private final SyncHeaderRequestManager syncHeaderRequestManager;

    private final Logger log;
    private final Logger surveyLog;

    private final int slowImportTime;
    private final int compactFrequency;

    private long lastCompactTime;

    TaskImportBlocks(
            final Logger syncLog,
            final Logger surveyLog,
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final SyncStats _syncStats,
            final PriorityBlockingQueue<BlocksWrapper> sortedBlocks,
            final Map<ByteArrayWrapper, Object> _importedBlockHashes,
            final SyncHeaderRequestManager syncHeaderRequestManager,
            final int _slowImportTime,
            final int _compactFrequency) {
        this.log = syncLog;
        this.surveyLog = surveyLog;
        this.chain = _chain;
        this.start = _start;
        this.syncStats = _syncStats;
        this.sortedBlocks = sortedBlocks;
        this.importedBlockHashes = _importedBlockHashes;
        this.syncHeaderRequestManager = syncHeaderRequestManager;
        this.slowImportTime = _slowImportTime;
        this.compactFrequency = _compactFrequency;
        this.lastCompactTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        // for runtime survey information
        long startTime, duration;

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (start.get()) {
            BlocksWrapper bw;
            try {
                startTime = System.nanoTime();
                bw = sortedBlocks.take();
                duration = System.nanoTime() - startTime;
                surveyLog.info("Import Stage 1.B: wait for sorted blocks, duration = {} ns.", duration);
            } catch (InterruptedException ex) {
                if (start.get()) {
                    log.error("Import blocks thread interrupted without shutdown request.", ex);
                }
                return;
            }

            startTime = System.nanoTime();
            SyncMode syncMode = syncHeaderRequestManager.getSyncMode(bw.nodeId);
            duration = System.nanoTime() - startTime;
            surveyLog.info("Import Stage 2: wait for peer state, duration = {} ns.", duration);

            if (syncMode == null) {
                // ignoring these blocks
                log.warn("Peer {} sent blocks that were not requested.", bw.displayId);
            } else { // the peerState is not null after this
                startTime = System.nanoTime();
                List<Block> batch = filterBatch(bw.blocks, chain, importedBlockHashes);
                duration = System.nanoTime() - startTime;
                surveyLog.info("Import Stage 3: filter batch, duration = {} ns.", duration);

                startTime = System.nanoTime();
                // process batch and update the peer state
                SyncMode newMode = processBatch(syncMode, batch, bw.displayId);
                duration = System.nanoTime() - startTime;
                surveyLog.info("Import Stage 4: process received and disk batches, duration = {} ns.", duration);

                // transition to recommended sync mode
                if (syncMode != newMode) {
                    syncHeaderRequestManager.runInMode(bw.nodeId, newMode);
                }

                syncStats.update(getBestBlockNumber());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "Thread ["
                            + Thread.currentThread().getName()
                            + "] performing block imports was shutdown.");
        }
    }

    /**
     * Utility method that takes a list of blocks and filters out the ones that are restricted for
     * import due to pruning and the ones that have already been imported recently.
     *
     * @param blocks the list of blocks to be filtered
     * @param chain the blockchain where the blocks will be imported which may impose pruning
     *     restrictions
     * @param imported the collection of recently imported blocks
     * @return the list of blocks that pass the filter conditions.
     */
    @VisibleForTesting
    static List<Block> filterBatch(
            List<Block> blocks,
            AionBlockchainImpl chain,
            Map<ByteArrayWrapper, Object> imported) {
        if (chain.hasPruneRestriction()) {
            // filter out restricted blocks if prune restrictions enabled
            return blocks.stream()
                    .filter(b -> isNotImported(b, imported))
                    .filter(b -> isNotRestricted(b, chain))
                    .collect(Collectors.toList());
        } else {
            // filter out only imported blocks
            return blocks.stream()
                    .filter(b -> isNotImported(b, imported))
                    .collect(Collectors.toList());
        }
    }

    private static boolean isNotImported(Block b, Map<ByteArrayWrapper, Object> imported) {
        return imported.get(ByteArrayWrapper.wrap(b.getHash())) == null;
    }

    private static boolean isNotRestricted(Block b, AionBlockchainImpl chain) {
        return !chain.isPruneRestricted(b.getNumber());
    }

    /** @implNote This method is called only when state is not null. */
    private SyncMode processBatch(SyncMode syncMode, List<Block> batch, String displayId) {
        // for runtime survey information
        long startTime, duration;

        // all blocks were filtered out
        // interpreted as repeated work
        if (batch.isEmpty()) {
            log.debug("Empty batch received from node = {} in mode = {}.", displayId, syncMode);

            // this transition is useful regardless of the given syncMode
            // the responses from multiple peers overlapped (because the batch was empty)
            // we therefore reset this peer to (possibly) do something other than its previous mode
            return NORMAL;
        }

        // the batch cannot be empty henceforth
        // check last block in batch to see if we can skip batch
        if (syncMode != BACKWARD) {
            Block b = batch.get(batch.size() - 1);

            // last block already exists
            // implies the full batch was already imported (but not filtered by the queue)
            if (isAlreadyStored(chain.getBlockStore(), b)) {
                // keeping track of the last block check
                importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);

                // skipping the batch
                log.debug("Skip {} blocks from node = {} in mode = {}.", batch.size(), displayId, syncMode);
                batch.clear();

                if (syncMode == FORWARD) {
                    return FORWARD;
                } else {
                    return NORMAL;
                }
            }
        }

        // remembering imported range
        long first = -1L, last = -1L;
        ImportResult importResult;
        SyncMode returnMode = syncMode;

        startTime = System.nanoTime();
        for (Block b : batch) {
            try {
                importResult = importBlock(b, displayId, syncMode);

                if (importResult.isStored()) {
                    importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);
                    this.syncStats.updatePeerBlocks(displayId, 1, BlockType.IMPORTED);

                    if (last <= b.getNumber()) {
                        last = b.getNumber() + 1;
                    }
                }
            } catch (Exception e) {
                log.error("<import-block throw> ", e);

                if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                    log.error("Shutdown due to lack of disk space.", e);
                    System.exit(SystemExitCodes.OUT_OF_DISK_SPACE);
                }
                break;
            }

            // decide whether to change mode based on the first
            if (b == batch.get(0)) {
                first = b.getNumber();

                // if any block results in NO_PARENT, all subsequent blocks will too
                if (importResult == ImportResult.NO_PARENT) {
                    storePendingBlocks(batch, displayId);

                    // check if it is below the current importable blocks
                    if (b.getNumber() <= getBestBlockNumber() + 1) {
                        duration = System.nanoTime() - startTime;
                        surveyLog.info("Import Stage 4.A: import received batch, duration = {} ns.", duration);
                        return BACKWARD;
                    }
                    duration = System.nanoTime() - startTime;
                    surveyLog.info("Import Stage 4.A: import received batch, duration = {} ns.", duration);
                    return returnMode;
                } else if (importResult.isStored()) {
                    if (syncMode == BACKWARD) {
                        returnMode = FORWARD;
                    } else if (syncMode == FORWARD && importResult.isBest()) {
                        returnMode = NORMAL;
                    }
                }
            }
        }
        duration = System.nanoTime() - startTime;
        surveyLog.info("Import Stage 4.A: import received batch, duration = {} ns.", duration);

        startTime = System.nanoTime();
        // check for stored blocks
        if (first < last) {
            returnMode = importFromStorage(returnMode, first, last);
        }
        duration = System.nanoTime() - startTime;
        surveyLog.info("Import Stage 4.B: process all disk batches, duration = {} ns.", duration);

        return returnMode;
    }

    /**
     * Utility method that determines if the given block is already stored in the given block store
     * without going through the process of trying to import the block.
     *
     * @param store the block store that may contain the given block
     * @param block the block for which we need to determine if it is already stored or not
     * @return {@code true} if the given block exists in the block store, {@code false} otherwise.
     * @apiNote Should be used when we aim to bypass any recovery methods set in place for importing
     *     old blocks, for example when blocks are imported in {@link SyncMode#FORWARD} mode.
     */
    static boolean isAlreadyStored(AionBlockStore store, Block block) {
        return store.getMaxNumber() >= block.getNumber() && store.isBlockStored(block.getHash(), block.getNumber());
    }

    private ImportResult importBlock(Block b, String displayId, SyncMode mode) {
        ImportResult importResult;
        long t1 = System.currentTimeMillis();
        importResult = this.chain.tryToConnect(b);
        long t2 = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            // printing sync mode only when debug is enabled
            log.debug(
                    "<import-status: node = {}, sync mode = {}, hash = {}, number = {}, txs = {}, block time = {}, result = {}, time elapsed = {} ms, td = {}>",
                    displayId,
                    mode,
                    b.getShortHash(),
                    b.getNumber(),
                    b.getTransactionsList().size(),
                    b.getTimestamp(),
                    importResult,
                    t2 - t1,
                    chain.getTotalDifficulty());
        } else {
            // not printing this message when the state is in fast mode with no parent result
            // a different message will be printed to indicate the storage of blocks
            if (log.isInfoEnabled() && (importResult != ImportResult.NO_PARENT)) {
                log.info(
                        "<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                        displayId,
                        b.getShortHash(),
                        b.getNumber(),
                        b.getTransactionsList().size(),
                        importResult,
                        t2 - t1);
            }
        }
        // trigger compact when IO is slow
        if (slowImportTime > 0 // disabled when set to <= 0
                && t2 - t1 > this.slowImportTime
                && t2 - lastCompactTime > this.compactFrequency) {
            if (log.isInfoEnabled()) {
                log.info("Compacting state database due to slow IO time.");
            }
            t1 = System.currentTimeMillis();
            this.chain.compactState();
            t2 = System.currentTimeMillis();
            if (log.isInfoEnabled()) {
                log.info("Compacting state completed in {} ms.", t2 - t1);
            }
            lastCompactTime = t2;
        }
        return importResult;
    }

    /**
     * Imports blocks from storage as long as there are blocks to import.
     *
     * @return the total number of imported blocks from all iterations
     */
    private SyncMode importFromStorage(SyncMode givenMode, long first, long last) {
        // for runtime survey information
        long startTime, duration;

        ImportResult importResult = ImportResult.NO_PARENT;
        int imported = 0, batch;
        long level = first;

        while (level <= last) {

            startTime = System.nanoTime();
            // get blocks stored for level
            Map<ByteArrayWrapper, List<Block>> levelFromDisk =
                    chain.loadPendingBlocksAtLevel(level);
            duration = System.nanoTime() - startTime;
            surveyLog.info("Import Stage 4.B.i: load batch from disk, duration = {} ns.", duration);

            if (levelFromDisk.isEmpty()) {
                // move on to next level
                level++;
                continue;
            }

            List<ByteArrayWrapper> importedQueues = new ArrayList<>(levelFromDisk.keySet());

            for (Map.Entry<ByteArrayWrapper, List<Block>> entry : levelFromDisk.entrySet()) {
                // initialize batch counter
                batch = 0;

                List<Block> batchFromDisk = entry.getValue();

                if (log.isDebugEnabled()) {
                    log.debug(
                            "Loaded {} blocks from disk from level {} queue {} before filtering.",
                            batchFromDisk.size(),
                            entry.getKey(),
                            level);
                }

                startTime = System.nanoTime();
                // filter already imported blocks
                batchFromDisk = filterBatch(batchFromDisk, chain, importedBlockHashes);
                duration = System.nanoTime() - startTime;
                surveyLog.info("Import Stage 4.B.ii: filter batch from disk, duration = {} ns.", duration);

                if (!batchFromDisk.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "{} {} left after filtering out imported blocks.",
                                batchFromDisk.size(),
                                (batchFromDisk.size() == 1 ? "block" : "blocks"));
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("No blocks left after filtering out imported blocks.");
                    }
                    // move on to next queue
                    // this queue will be deleted from storage
                    continue;
                }

                startTime = System.nanoTime();
                for (Block b : batchFromDisk) {
                    try {
                        importResult = importBlock(b, "STORAGE", givenMode);

                        if (importResult.isStored()) {
                            importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);

                            batch++;

                            if (last == b.getNumber()) {
                                // can try importing more
                                last = b.getNumber() + 1;
                            }
                        } else {
                            // do not delete queue from storage
                            importedQueues.remove(entry.getKey());
                            // stop importing this queue
                            break;
                        }
                    } catch (Exception e) {
                        log.error("<import-block throw> ", e);
                        if (e.getMessage() != null
                                && e.getMessage().contains("No space left on device")) {
                            log.error("Shutdown due to lack of disk space.", e);
                            System.exit(SystemExitCodes.OUT_OF_DISK_SPACE);
                        }
                    }
                }
                duration = System.nanoTime() - startTime;
                surveyLog.info("Import Stage 4.B.iii: import batch from disk, duration = {} ns.", duration);

                imported += batch;
            }

            // remove imported data from storage
            dropImportedBlocks(level, importedQueues, levelFromDisk);

            // increment level
            level++;
        }

        log.debug("Imported {} blocks from storage.", imported);

        // switch to NORMAL if in FORWARD mode
        if (importResult.isBest()) {
            return NORMAL;
        } else if (importResult.isStored() && givenMode == BACKWARD) {
            return FORWARD;
        }

        return givenMode;
    }

    private long getBestBlockNumber() {
        return chain.getBestBlock() == null ? 0 : chain.getBestBlock().getNumber();
    }

    private void storePendingBlocks(final List<Block> batch, final String displayId) {
        Block first = batch.get(0);
        int stored = chain.storePendingBlockRange(batch);
        this.syncStats.updatePeerBlocks(displayId, stored, BlockType.STORED);

        // log operation
        log.info(
                "<import-status: STORED {} out of {} blocks from node = {}, starting with hash = {}, number = {}, txs = {}>",
                stored,
                batch.size(),
                displayId,
                first.getShortHash(),
                first.getNumber(),
                first.getTransactionsList().size());
    }

    private void dropImportedBlocks(
            final long level,
            final List<ByteArrayWrapper> importedQueues,
            final Map<ByteArrayWrapper, List<Block>> levelFromDisk) {

        chain.dropImported(level, importedQueues, levelFromDisk);

        // log operation
        log.debug(
                "Dropped from storage level = {} with queues = {}.",
                level,
                Arrays.toString(importedQueues.toArray()));
    }
}
