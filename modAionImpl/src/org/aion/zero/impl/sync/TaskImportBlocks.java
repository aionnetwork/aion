/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.sync;

import static org.aion.p2p.P2pConstant.COEFFICIENT_NORMAL_PEERS;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.MAX_NORMAL_PEERS;
import static org.aion.p2p.P2pConstant.MIN_NORMAL_PEERS;
import static org.aion.zero.impl.sync.PeerState.Mode.BACKWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.FORWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.LIGHTNING;
import static org.aion.zero.impl.sync.PeerState.Mode.NORMAL;
import static org.aion.zero.impl.sync.PeerState.Mode.THUNDER;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.ImportResult;
import org.aion.p2p.P2pConstant;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.sync.PeerState.Mode;
import org.aion.zero.impl.types.AionBlock;
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

    private final BlockingQueue<BlocksWrapper> downloadedBlocks;

    private final SyncStats stats;

    private final Map<ByteArrayWrapper, Object> importedBlockHashes;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    private SortedSet<Long> baseList;
    private PeerState state;

    private final int slowImportTime;
    private final int compactFrequency;

    private long lastCompactTime;

    TaskImportBlocks(
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final SyncStats _stats,
            final BlockingQueue<BlocksWrapper> _downloadedBlocks,
            final Map<ByteArrayWrapper, Object> _importedBlockHashes,
            final Map<Integer, PeerState> _peerStates,
            final Logger _log,
            final int _slowImportTime,
            final int _compactFrequency) {
        this.chain = _chain;
        this.start = _start;
        this.stats = _stats;
        this.downloadedBlocks = _downloadedBlocks;
        this.importedBlockHashes = _importedBlockHashes;
        this.peerStates = _peerStates;
        this.log = _log;
        this.baseList = new TreeSet<>();
        this.state = new PeerState(NORMAL, 0L);
        this.slowImportTime = _slowImportTime;
        this.compactFrequency = _compactFrequency;
        this.lastCompactTime = System.currentTimeMillis();
    }

    ExecutorService executors =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (start.get()) {
            BlocksWrapper bw;
            try {
                bw = downloadedBlocks.take();
            } catch (InterruptedException ex) {
                if (start.get()) {
                    log.error("Import blocks thread interrupted without shutdown request.", ex);
                }
                return;
            }

            PeerState peerState = peerStates.get(bw.getNodeIdHash());
            if (peerState == null) {
                // ignoring these blocks
                log.warn("Peer {} sent blocks that were not requested.", bw.getDisplayId());
            } else { // the peerState is not null after this
                List<AionBlock> batch = filterBatch(bw.getBlocks(), chain, importedBlockHashes);

                if (log.isDebugEnabled()) {
                    log.debug(
                            "<import-mode-before: node = {}, sync mode = {}, base = {}>",
                            bw.getDisplayId(),
                            peerState.getMode(),
                            peerState.getBase());
                }

                // process batch and update the peer state
                peerState.copy(processBatch(peerState, batch, bw.getDisplayId()));

                // so we can continue immediately
                peerState.resetLastHeaderRequest();

                if (log.isDebugEnabled()) {
                    log.debug(
                            "<import-mode-after: node = {}, sync mode = {}, base = {}>",
                            bw.getDisplayId(),
                            peerState.getMode(),
                            peerState.getBase());
                }

                stats.update(getBestBlockNumber());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "Thread ["
                            + Thread.currentThread().getName()
                            + "] performing block imports was shutdown.");
        }
        executors.shutdown();
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
    static List<AionBlock> filterBatch(
            List<AionBlock> blocks,
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

    private static boolean isNotImported(AionBlock b, Map<ByteArrayWrapper, Object> imported) {
        return imported.get(ByteArrayWrapper.wrap(b.getHash())) == null;
    }

    private static boolean isNotRestricted(AionBlock b, AionBlockchainImpl chain) {
        return !chain.isPruneRestricted(b.getNumber());
    }

    /** @implNote This method is called only when state is not null. */
    private PeerState processBatch(PeerState givenState, List<AionBlock> batch, String displayId) {
        // make a copy of the original state
        state.copy(Objects.requireNonNull(givenState));

        // new batch received -> add another iteration to the count
        state.incRepeated();

        // all blocks were filtered out
        // interpreted as repeated work
        if (batch.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Empty batch received from node = {} in mode = {} with base = {}.",
                        displayId,
                        givenState.getMode(),
                        givenState.getBase());
            }

            if (state.getMode() == BACKWARD || state.getMode() == FORWARD) {
                // multiple peers are doing the same BACKWARD/FORWARD pass
                // TODO: verify that this improves efficiency
                // TODO: impact of allowing the LIGHTNING jump instead?
                state.setMode(NORMAL);
                return state;
            } else {
                return attemptLightningJump(
                        getBestBlockNumber(), state, peerStates.values(), baseList, chain);
            }
        }

        // the batch cannot be empty henceforth
        // check last block in batch to see if we can skip batch
        if (givenState.getMode() != BACKWARD) {
            AionBlock b = batch.get(batch.size() - 1);
            Mode mode = givenState.getMode();

            // last block already exists
            // implies the full batch was already imported (but not filtered by the queue)
            if (isAlreadyStored(chain.getBlockStore(), b)) {
                // keeping track of the last block check
                importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);

                // skipping the batch
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Skip {} blocks from node = {} in mode = {} with base = {}.",
                            batch.size(),
                            displayId,
                            givenState.getMode(),
                            givenState.getBase());
                }
                batch.clear();

                // updating the state
                if (mode == FORWARD) {
                    return forwardModeUpdate(state, b.getNumber(), ImportResult.EXIST);
                } else {
                    // mode in { NORMAL, LIGHTNING, THUNDER }
                    return attemptLightningJump(
                            getBestBlockNumber(), state, peerStates.values(), baseList, chain);
                }
            }
        }

        // remembering imported range
        long first = -1L, last = -1L;
        ImportResult importResult;

        for (AionBlock b : batch) {
            try {
                importResult = importBlock(b, displayId, givenState);

                if (importResult.isStored()) {
                    importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);

                    if (last <= b.getNumber()) {
                        last = b.getNumber() + 1;
                    }
                }
            } catch (Exception e) {
                log.error("<import-block throw> ", e);
                if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                    log.error("Shutdown due to lack of disk space.");
                    System.exit(0);
                }
                break;
            }

            // decide whether to change mode based on the first
            if (b == batch.get(0)) {
                first = b.getNumber();
                Mode mode = givenState.getMode();

                // if any block results in NO_PARENT, all subsequent blocks will too
                if (importResult == ImportResult.NO_PARENT) {
                    executors.submit(new TaskStorePendingBlocks(chain, batch, displayId, log));

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Stopped importing batch due to NO_PARENT result. "
                                        + "Batch of {} blocks starting at hash = {}, number = {} from node = {} delegated to storage.",
                                batch.size(),
                                b.getShortHash(),
                                b.getNumber(),
                                displayId);
                    } else {
                        // message used instead of import NO_PARENT ones
                        if (state.isInFastMode()) {
                            log.info(
                                    "<import-status: STORED {} blocks from node = {}, starting with hash = {}, number = {}, txs = {}>",
                                    batch.size(),
                                    displayId,
                                    b.getShortHash(),
                                    b.getNumber(),
                                    b.getTransactionsList().size());
                        }
                    }

                    switch (mode) {
                        case FORWARD:
                            {
                                // switch to backward mode
                                state.setMode(BACKWARD);
                                state.setBase(b.getNumber());
                                break;
                            }
                        case NORMAL:
                            {
                                // requiring a minimum number of normal states
                                if (countStates(getBestBlockNumber(), NORMAL, peerStates.values())
                                        > MIN_NORMAL_PEERS) {
                                    // switch to backward mode
                                    state.setMode(BACKWARD);
                                    state.setBase(b.getNumber());
                                }
                                break;
                            }
                        case BACKWARD:
                            {
                                // update base
                                state.setBase(b.getNumber());
                                break;
                            }
                        case LIGHTNING:
                            {
                                state.setBase(b.getNumber() + batch.size());
                                break;
                            }
                        case THUNDER:
                            break;
                    }
                    // exit loop after NO_PARENT result
                    break;
                } else if (importResult.isStored()) {
                    // assuming the remaining blocks will be imported. if not, the state
                    // and base will be corrected by the next cycle
                    long lastBlock = batch.get(batch.size() - 1).getNumber();

                    switch (mode) {
                        case BACKWARD:
                            // we found the fork point
                            state.setMode(FORWARD);
                            state.setBase(lastBlock);
                            break;
                        case FORWARD:
                            state = forwardModeUpdate(state, lastBlock, importResult);
                            break;
                        case LIGHTNING:
                        case THUNDER:
                            state =
                                    attemptLightningJump(
                                            getBestBlockNumber(),
                                            state,
                                            peerStates.values(),
                                            baseList,
                                            chain);
                            break;
                        case NORMAL:
                        default:
                            break;
                    }
                }
            }
        }

        // check for stored blocks
        if (first < last) {
            int imported = importFromStorage(state, first, last);
            if (imported > 0) {
                // TODO: may have already updated torrent mode
                if (state.getMode() == LIGHTNING) {
                    if (state.getBase() == givenState.getBase() // was not already updated
                            || state.getBase() <= getBestBlockNumber() + P2pConstant.REQUEST_SIZE) {
                        state =
                                attemptLightningJump(
                                        getBestBlockNumber(),
                                        state,
                                        peerStates.values(),
                                        baseList,
                                        chain);
                    } // else already updated to a correct request
                    return state;
                } else if (state.getMode() == BACKWARD || state.getMode() == FORWARD) {
                    // TODO: verify that this improves efficiency
                    // TODO: impact of allowing the LIGHTNING jump instead?
                    state.setMode(NORMAL);
                    return state;
                }
            }
        }

        return state;
    }

    /**
     * Utility method that updates the given state to a LIGHTNING jump when the jump conditions
     * (balancing the number of fast and normal states) are met. If a jump is not possible (due to
     * the requirement of having a best block status larger than the selected base value) for a
     * state that is already in LIGHTNING mode, the state is changed to THUNDER mode.
     *
     * @param best the starting point value for the attempted jump
     * @param state the state to be modified for the jump or ramp down
     * @param states all the existing peer states are the time of the method call used for checking
     *     if the jump conditions are met
     * @param baseSet sorted set of generated values that can be used as base for the jump
     * @param chain the blockchain where the blocks will be imported which can be used to expand the
     *     set of base value options
     * @return a state modified for a LIGHTNING when possible, otherwise a state in THUNDER (ramp
     *     down) mode if the state was previously in LIGHTNING mode, or an unchanged state when none
     *     of the before mentioned conditions are met.
     * @implNote Typically called when {@link PeerState#getMode()} in { {@link
     *     PeerState.Mode#NORMAL}, {@link PeerState.Mode#LIGHTNING}, {@link PeerState.Mode#THUNDER}
     *     }, but the same behaviour of jumping ahead will be applied if the give state mode is
     *     {@link PeerState.Mode#BACKWARD} or {@link PeerState.Mode#FORWARD}.
     */
    static PeerState attemptLightningJump(
            long best,
            PeerState state,
            Collection<PeerState> states,
            SortedSet<Long> baseSet,
            AionBlockchainImpl chain) {

        // no need to count states if already in LIGHTNING
        if (state.getMode() == LIGHTNING) {
            // select the base to be used
            long nextBase = selectBase(best, state.getLastBestBlock(), baseSet, chain);

            // determine if base is future block
            if (nextBase > best) {
                // determine if a jump is possible
                if (state.getLastBestBlock() > nextBase + LARGE_REQUEST_SIZE) {
                    // new jump resets the repeated count
                    state.setMode(LIGHTNING);
                    state.setBase(nextBase);
                } else {
                    // can't jump so ramp down
                    state.setMode(THUNDER);
                    // recycle unused base
                    baseSet.add(nextBase);
                }
            } else {
                // can't jump so ramp down
                state.setMode(THUNDER);
            }
        } else {
            // compute the relevant state count
            long normalStates =
                    countStates(best, NORMAL, states) + countStates(best, THUNDER, states);
            long fastStates = countStates(best, LIGHTNING, states);

            // requiring a minimum number of normal states
            if (normalStates > MIN_NORMAL_PEERS
                    // the fast vs normal states balance depends on the give coefficient
                    && (fastStates < COEFFICIENT_NORMAL_PEERS * normalStates
                            // with a maximum number of normal states
                            || normalStates > MAX_NORMAL_PEERS)) {

                // select the base to be used
                long nextBase = selectBase(best, state.getLastBestBlock(), baseSet, chain);

                // determine if base is future block
                if (nextBase > best) {
                    // determine if a jump is possible
                    if (state.getLastBestBlock() > nextBase + LARGE_REQUEST_SIZE) {
                        state.setMode(LIGHTNING);
                        state.setBase(nextBase);
                    } else {
                        // recycle unused base
                        baseSet.add(nextBase);
                    }
                }
            }
        }
        return state;
    }

    /**
     * Utility method that computes the number of states from the given ones that have the give mode
     * and a last best block status larger than the given number.
     *
     * @param states the list of peer states to be explored
     * @param mode the state mode we are searching for
     * @param best the minimum accepted last best block status for the peer
     * @return the number of states that satisfy the condition above.
     */
    static long countStates(long best, Mode mode, Collection<PeerState> states) {
        return states.stream()
                .filter(s -> s.getLastBestBlock() > best)
                .filter(s -> s.getMode() == mode)
                .count();
    }

    /**
     * Utility method that selects a number greater or equal to the given best representing the base
     * value for the next LIGHTNING request. The returned base will be either retrieved from the set
     * of previously generated values that have not yet been used or a new value generated by
     * calling the given chain's {@link AionBlockchainImpl#nextBase(long, long)} method.
     *
     * @param best the starting point value for the next base
     * @param baseSet list of already generated values
     * @param chain the blockchain where the blocks will be imported which can be used to expand the
     *     set of base value options
     * @return the next base from the set or the given best value when the set does not contain any
     *     values greater than it.
     */
    static long selectBase(
            long best, long knownStatus, SortedSet<Long> baseSet, AionBlockchainImpl chain) {
        // remove bases that are no longer relevant
        while (!baseSet.isEmpty() && baseSet.first() <= best) {
            baseSet.remove(baseSet.first());
        }

        if (baseSet.isEmpty()) {
            // generate new possible base value
            return chain.nextBase(best, knownStatus);
        } else {
            Long first = baseSet.first();
            baseSet.remove(first);
            return first;
        }
    }

    /**
     * Utility method that determines if the given block is already stored in the given block store
     * without going through the process of trying to import the block.
     *
     * @param store the block store that may contain the given block
     * @param block the block for which we need to determine if it is already stored or not
     * @return {@code true} if the given block exists in the block store, {@code false} otherwise.
     * @apiNote Should be used when we aim to bypass any recovery methods set in place for importing
     *     old blocks, for example when blocks are imported in {@link PeerState.Mode#FORWARD} mode.
     */
    static boolean isAlreadyStored(AionBlockStore store, AionBlock block) {
        return store.getMaxNumber() >= block.getNumber() && store.isBlockExist(block.getHash());
    }

    private ImportResult importBlock(AionBlock b, String displayId, PeerState state) {
        ImportResult importResult;
        long t1 = System.currentTimeMillis();
        importResult = this.chain.tryToConnect(b);
        long t2 = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            // printing sync mode only when debug is enabled
            log.debug(
                    "<import-status: node = {}, sync mode = {}, hash = {}, number = {}, txs = {}, block time = {}, result = {}, time elapsed = {} ms>",
                    displayId,
                    (state != null ? state.getMode() : NORMAL),
                    b.getShortHash(),
                    b.getNumber(),
                    b.getTransactionsList().size(),
                    b.getTimestamp(),
                    importResult,
                    t2 - t1);
        } else {
            // not printing this message when the state is in fast mode with no parent result
            // a different message will be printed to indicate the storage of blocks
            if (!state.isInFastMode() || importResult != ImportResult.NO_PARENT) {
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
        if (t2 - t1 > this.slowImportTime && t2 - lastCompactTime > this.compactFrequency) {
            log.info("Compacting state database due to slow IO time.");
            t1 = System.currentTimeMillis();
            this.chain.compactState();
            t2 = System.currentTimeMillis();
            log.info("Compacting state completed in {} ms.", t2 - t1);
            lastCompactTime = t2;
        }
        return importResult;
    }

    /**
     * Utility method that sets the base for the next FORWARD request OR switches to NORMAL mode
     * when (1) a block import resulted in an IMPORTED_BEST result or (2) the maximum number of
     * repetitions has been reached.
     *
     * @implNote Reaching the maximum number of repetitions allowed means that the FORWARD requests
     *     have covered the scope of blocks between the BACKWARD request that has had a NO_PARENT
     *     result and the subsequent BACKWARD request that got an EXIST / IMPORTED_BEST /
     *     IMPORTED_NOT_BEST result. Effectively covering this space without storing the blocks
     *     means that either an error has occurred or that another peer has already imported these
     *     blocks. The second scenario is the most likely which makes switching to NORMAL mode the
     *     natural consequence.
     * @param state the peer state to be updated
     * @param lastBlock the last imported block number
     * @param importResult the result for the last imported block
     * @return an updated state according to the description above.
     */
    static PeerState forwardModeUpdate(PeerState state, long lastBlock, ImportResult importResult) {
        // when the maximum number of repeats has passed
        // the peer is stuck behind other peers importing the same (old) blocks
        if (importResult.isBest() || !state.isUnderRepeatThreshold()) {
            state.setMode(NORMAL);
        } else {
            // in case we continue as FORWARD
            state.setBase(lastBlock);
        }

        return state;
    }

    /**
     * Imports blocks from storage as long as there are blocks to import.
     *
     * @return the total number of imported blocks from all iterations
     */
    private int importFromStorage(PeerState state, long first, long last) {
        ImportResult importResult = ImportResult.NO_PARENT;
        int imported = 0, batch;
        long level = first;

        while (level <= last) {
            // get blocks stored for level
            Map<ByteArrayWrapper, List<AionBlock>> levelFromDisk =
                    chain.loadPendingBlocksAtLevel(level);

            if (levelFromDisk.isEmpty()) {
                // move on to next level
                level++;
                continue;
            }

            List<ByteArrayWrapper> importedQueues = new ArrayList<>(levelFromDisk.keySet());

            for (Map.Entry<ByteArrayWrapper, List<AionBlock>> entry : levelFromDisk.entrySet()) {
                // initialize batch counter
                batch = 0;

                List<AionBlock> batchFromDisk = entry.getValue();

                if (log.isDebugEnabled()) {
                    log.debug(
                            "Loaded {} blocks from disk from level {} queue {} before filtering.",
                            batchFromDisk.size(),
                            entry.getKey(),
                            level);
                }

                // filter already imported blocks
                batchFromDisk = filterBatch(batchFromDisk, chain, importedBlockHashes);

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

                for (AionBlock b : batchFromDisk) {
                    try {
                        importResult = importBlock(b, "STORAGE", state);

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
                            log.error("Shutdown due to lack of disk space.");
                            System.exit(0);
                        }
                    }
                }

                imported += batch;
            }

            // remove imported data from storage
            executors.submit(
                    new TaskDropImportedBlocks(chain, level, importedQueues, levelFromDisk, log));

            // increment level
            level++;
        }

        // switch to NORMAL if in FORWARD mode
        if (importResult.isBest() && state.getMode() == FORWARD) {
            state.setMode(NORMAL);
        }

        return imported;
    }

    private long getBestBlockNumber() {
        return chain.getBestBlock() == null ? 0 : chain.getBestBlock().getNumber();
    }
}
