package org.aion.zero.impl.sync;

import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.BACKWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.FORWARD;
import static org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode.NORMAL;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.sync.SyncHeaderRequestManager.SyncMode;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;

/**
 * handle process of importing blocks to repo
 *
 * <p>TODO: targeted send
 *
 * @author chris
 */
final class TaskImportBlocks {

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.SYNC.name());
    private static final Logger surveyLog = AionLoggerFactory.getLogger(LogEnum.SURVEY.name());

    static void importBlocks(final AionBlockchainImpl chain, final SyncStats syncStats, final BlocksWrapper bw, final Map<ByteArrayWrapper, Object> importedBlockHashes, final SyncHeaderRequestManager syncHeaderRequestManager) {
        Thread.currentThread().setName("sync-ib");

        long startTime = System.nanoTime();
        SyncMode syncMode = syncHeaderRequestManager.getSyncMode(bw.nodeId);
        long duration = System.nanoTime() - startTime;
        surveyLog.debug("Import Stage 2: wait for peer state, duration = {} ns.", duration);

        if (syncMode == null) {
            // ignoring these blocks
            log.warn("Peer {} sent blocks that were not requested.", bw.displayId);
        } else { // the peerState is not null after this
            startTime = System.nanoTime();
            List<Block> batch = filterBatch(bw.blocks, chain, importedBlockHashes);
            duration = System.nanoTime() - startTime;
            surveyLog.debug("Import Stage 3: filter batch, duration = {} ns.", duration);

            startTime = System.nanoTime();
            // process batch and update the peer state
            SyncMode newMode = processBatch(chain, importedBlockHashes, syncStats, syncMode, batch, bw.displayId);
            duration = System.nanoTime() - startTime;
            surveyLog.debug("Import Stage 4: process received and disk batches, duration = {} ns.", duration);

            // transition to recommended sync mode
            if (syncMode != newMode) {
                syncHeaderRequestManager.runInMode(bw.nodeId, newMode);
            }

            syncStats.update(getBestBlockNumber(chain));
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
    private static SyncMode processBatch(AionBlockchainImpl chain, Map<ByteArrayWrapper, Object> importedBlockHashes, SyncStats syncStats, SyncMode syncMode, List<Block> batch, String displayId) {
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
            if (chain.isBlockStored(b.getHash(), b.getNumber())) {
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
        Block firstInBatch = batch.get(0);
        long first = firstInBatch.getNumber(), last = -1L, currentBest;
        ImportResult importResult = null;
        SyncMode returnMode = syncMode;

        startTime = System.nanoTime();
        try {
            long importDuration = System.currentTimeMillis();
            Triple<Long, Set<ByteArrayWrapper>, ImportResult> resultTriple = chain.tryToConnect(batch, displayId);
            importDuration = System.currentTimeMillis() - importDuration;

            currentBest = resultTriple.getLeft();
            Set<ByteArrayWrapper> importedHashes = resultTriple.getMiddle();
            importResult = resultTriple.getRight();

            int count = importedHashes.size();
            if (currentBest >= first) {
                last = currentBest + 1;
                importedHashes.stream().forEach(v -> importedBlockHashes.put(v, true));
                syncStats.updatePeerBlocks(displayId, count, BlockType.IMPORTED);
                log.info("<import-status: node = {}, from = #{}, to = #{}, time elapsed = {} ms>", displayId, first, currentBest, importDuration);
            }
        } catch (Exception e) {
            log.error("<import-block throw> ", e);

            if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                log.error("Shutdown due to lack of disk space.", e);
                System.exit(SystemExitCodes.OUT_OF_DISK_SPACE);
            }
        }

        // if any block results in NO_PARENT, all subsequent blocks will too
        if (importResult == ImportResult.NO_PARENT) {
            int stored = chain.storePendingBlockRange(batch, log);
            syncStats.updatePeerBlocks(displayId, stored, BlockType.STORED);

            // check if it is below the current importable blocks
            if (firstInBatch.getNumber() <= getBestBlockNumber(chain) + 1) {
                duration = System.nanoTime() - startTime;
                surveyLog.debug("Import Stage 4.A: import received batch, duration = {} ns.", duration);
                return BACKWARD;
            }
            duration = System.nanoTime() - startTime;
            surveyLog.debug("Import Stage 4.A: import received batch, duration = {} ns.", duration);
            return returnMode;
        } else if (importResult.isStored()) {
            if (syncMode == BACKWARD) {
                returnMode = FORWARD;
            } else if (syncMode == FORWARD && importResult.isBest()) {
                returnMode = NORMAL;
            }
        }
        duration = System.nanoTime() - startTime;
        surveyLog.debug("Import Stage 4.A: import received batch, duration = {} ns.", duration);

        startTime = System.nanoTime();
        // check for stored blocks
        if (first < last) {
            returnMode = importFromStorage(chain, importedBlockHashes, returnMode, first, last);
        }
        duration = System.nanoTime() - startTime;
        surveyLog.debug("Import Stage 4.B: process all disk batches, duration = {} ns.", duration);

        return returnMode;
    }

    /**
     * Imports blocks from storage as long as there are blocks to import.
     *
     * @return the total number of imported blocks from all iterations
     */
    private static SyncMode importFromStorage(AionBlockchainImpl chain,Map<ByteArrayWrapper, Object> importedBlockHashes, SyncMode givenMode, long first, long last) {
        // for runtime survey information
        long startTime, duration;

        ImportResult importResult = ImportResult.NO_PARENT;
        int imported = 0, batch;
        long level = first;

        while (level <= last) {

            startTime = System.nanoTime();
            // get blocks stored for level
            Map<ByteArrayWrapper, List<Block>> levelFromDisk = chain.loadPendingBlocksAtLevel(level, log);
            duration = System.nanoTime() - startTime;
            surveyLog.debug("Import Stage 4.B.i: load batch from disk, duration = {} ns.", duration);

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

                startTime = System.nanoTime();
                // filter already imported blocks
                batchFromDisk = filterBatch(batchFromDisk, chain, importedBlockHashes);
                duration = System.nanoTime() - startTime;
                surveyLog.debug("Import Stage 4.B.ii: filter batch from disk, duration = {} ns.", duration);

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
                try {
                    first = batchFromDisk.get(0).getNumber();
                    long importDuration = System.currentTimeMillis();
                    Triple<Long, Set<ByteArrayWrapper>, ImportResult> resultTriple = chain.tryToConnect(batchFromDisk, "STORAGE");
                    importDuration = System.currentTimeMillis() - importDuration;

                    long currentBest = resultTriple.getLeft();
                    Set<ByteArrayWrapper> importedHashes = resultTriple.getMiddle();
                    importResult = resultTriple.getRight();

                    batch = importedHashes.size();
                    if (currentBest >= first) {
                        last = currentBest + 1;
                        importedHashes.stream().forEach(v -> importedBlockHashes.put(v, true));
                        log.info("<import-status: node = {}, from = #{}, to = #{}, time elapsed = {} ms>", "STORAGE", first, currentBest, importDuration);
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
                duration = System.nanoTime() - startTime;
                surveyLog.debug("Import Stage 4.B.iii: import batch from disk, duration = {} ns.", duration);

                imported += batch;
            }

            // remove imported data from storage
            chain.dropImported(level, importedQueues, levelFromDisk, log);

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

    private static long getBestBlockNumber(AionBlockchainImpl chain) {
        return chain.getBestBlock() == null ? 0 : chain.getBestBlock().getNumber();
    }
}
