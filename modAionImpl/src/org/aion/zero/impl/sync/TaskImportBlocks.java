/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 */

package org.aion.zero.impl.sync;

import static org.aion.p2p.P2pConstant.COEFFICIENT_FAST_PEERS;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.MIN_NORMAL_PEERS;
import static org.aion.zero.impl.sync.PeerState.Mode.BACKWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.FORWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.LIGHTNING;
import static org.aion.zero.impl.sync.PeerState.Mode.NORMAL;
import static org.aion.zero.impl.sync.PeerState.Mode.THUNDER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.ImportResult;
import org.aion.p2p.P2pConstant;
import org.aion.zero.impl.AionBlockchainImpl;
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

    private final SyncStatics statis;

    private final Map<ByteArrayWrapper, Object> importedBlockHashes;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    private List<Long> baseList;
    private PeerState state;

    TaskImportBlocks(
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final SyncStatics _statis,
            final BlockingQueue<BlocksWrapper> _downloadedBlocks,
            final Map<ByteArrayWrapper, Object> _importedBlockHashes,
            final Map<Integer, PeerState> _peerStates,
            final Logger _log) {
        this.chain = _chain;
        this.start = _start;
        this.statis = _statis;
        this.downloadedBlocks = _downloadedBlocks;
        this.importedBlockHashes = _importedBlockHashes;
        this.peerStates = _peerStates;
        this.log = _log;
        this.baseList = new ArrayList<>();
        this.state = new PeerState(NORMAL, 0L);
    }

    private boolean isNotImported(AionBlock b) {
        return importedBlockHashes.get(ByteArrayWrapper.wrap(b.getHash())) == null;
    }

    private boolean isNotRestricted(AionBlock b) {
        return !chain.isPruneRestricted(b.getNumber());
    }

    /** Returns either a recycled base or generates a new one. */
    private long getNextBase() {
        long best = getBestBlockNumber();

        // remove bases that are no longer relevant
        while (!baseList.isEmpty() && baseList.get(0) <= best) {
            baseList.remove(0);
        }

        if (baseList.isEmpty()) {
            return chain.nextBase(best);
        } else {
            return baseList.remove(0);
        }
    }

    /** Add an unused base to the list. */
    private void recycleNextBase(long base) {
        baseList.add(base);
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (start.get()) {
            BlocksWrapper bw;
            try {
                bw = downloadedBlocks.take();
            } catch (InterruptedException ex) {
                return;
            }

            List<AionBlock> batch;

            if (chain.checkPruneRestriction()) {
                // filter out restricted blocks if prune restrictions enabled
                batch =
                        bw.getBlocks()
                                .stream()
                                .filter(this::isNotImported)
                                .filter(this::isNotRestricted)
                                .collect(Collectors.toList());
            } else {
                // filter out only imported blocks
                batch =
                        bw.getBlocks()
                                .stream()
                                .filter(this::isNotImported)
                                .collect(Collectors.toList());
            }

            PeerState peerState = peerStates.get(bw.getNodeIdHash());
            if (peerState == null) {
                // ignoring these blocks
                log.warn("Peer {} sent blocks that were not requested.", bw.getDisplayId());
            } else { // the peerState is not null after this
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

                statis.update(getBestBlockNumber());
            }
        }
        log.info(Thread.currentThread().getName() + " RIP.");
    }

    /** @implNote Typically called when state.getMode() in { NORMAL, LIGHTNING, THUNDER }. */
    private PeerState attemptLightningJump(Collection<PeerState> all, PeerState state, long best) {
        long normalStates = getStateCount(all, NORMAL, best) + getStateCount(all, THUNDER, best);
        long fastStates = getStateCount(all, LIGHTNING, best);

        state.incRepeated();
        // in NORMAL mode and blocks filtered out
        if (normalStates > MIN_NORMAL_PEERS && COEFFICIENT_FAST_PEERS * fastStates < normalStates) {
            // targeting around same number of LIGHTNING and NORMAL sync nodes
            // with a minimum of 4 NORMAL nodes
            long nextBase = getNextBase();
            if (state.getLastBestBlock() > nextBase + LARGE_REQUEST_SIZE) {
                state.setMode(LIGHTNING);
                state.setBase(nextBase);
            } else {
                recycleNextBase(nextBase);
                if (state.getMode() == LIGHTNING) {
                    // can't jump so ramp down
                    state.setMode(THUNDER);
                }
            }
        }
        return state;
    }

    /**
     * Computes the number of states from the given ones that have the give mode and a last best
     * block status larger than the given number.
     *
     * @param states the list of peer states to be explored
     * @param mode the state mode we are searching for
     * @param best the minimum accepted last best block status for the peer
     * @return the number of states that satisfy the condition above.
     */
    static long getStateCount(Collection<PeerState> states, Mode mode, long best) {
        return states.stream()
                .filter(s -> s.getLastBestBlock() > best)
                .filter(s -> s.getMode() == mode)
                .count();
    }

    private boolean wasPreviouslyStored(AionBlock block) {
        return chain.getBlockStore().getMaxNumber() >= block.getNumber()
                && chain.getBlockStore().isBlockExist(block.getHash());
    }

    /** @implNote This method is called only when state is not null. */
    private PeerState processBatch(PeerState givenState, List<AionBlock> batch, String displayId) {
        // make a copy of the original state
        state.copy(givenState);

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
                return attemptLightningJump(peerStates.values(), state, getBestBlockNumber());
            }
        }

        // the batch cannot be empty henceforth
        // check last block in batch to see if we can skip batch
        if (givenState.getMode() != BACKWARD) {
            AionBlock b = batch.get(batch.size() - 1);
            Mode mode = givenState.getMode();

            // last block exists when in FORWARD mode
            if ((mode == FORWARD && wasPreviouslyStored(b))
                    // late returns on main chain requests
                    // where the blocks are behind the local chain and can be discarded
                    || (mode != FORWARD && b.getNumber() < getBestBlockNumber())) {

                // keeping track of the last block check
                importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);

                // skipping the batch
                batch.clear();
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Skip batch for node = {} in mode = {} with base = {}.",
                            displayId,
                            givenState.getMode(),
                            givenState.getBase());
                }

                // updating the state
                if (mode == FORWARD) {
                    return forwardModeUpdate(
                            state, b.getNumber(), ImportResult.EXIST, b.getNumber());
                } else {
                    // mode in { NORMAL, LIGHTNING, THUNDER }
                    return attemptLightningJump(peerStates.values(), state, getBestBlockNumber());
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
            } catch (Throwable e) {
                log.error("<import-block throw> {}", e.toString());
                if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                    log.error("Shutdown due to lack of disk space.");
                    System.exit(0);
                }
                // TODO test and determine consequences: continue or break?
                continue;
            }

            // decide whether to change mode based on the first
            if (b == batch.get(0)) {
                first = b.getNumber();
                Mode mode = givenState.getMode();

                // if any block results in NO_PARENT, all subsequent blocks will too
                if (importResult == ImportResult.NO_PARENT) {
                    int stored = chain.storePendingBlockRange(batch);

                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Stopped importing batch due to NO_PARENT result.\n"
                                        + "Stored {} out of {} blocks starting at hash = {}, number = {} from node = {}.",
                                stored,
                                batch.size(),
                                b.getShortHash(),
                                b.getNumber(),
                                displayId);
                    }

                    switch (mode) {
                        case FORWARD:
                        case NORMAL:
                            {
                                // switch to backward mode
                                state.setMode(BACKWARD);
                            }
                        case BACKWARD:
                            {
                                // update base
                                state.setBase(b.getNumber());
                                break;
                            }
                        case LIGHTNING:
                            {
                                if (stored < batch.size()) {
                                    state =
                                            attemptLightningJump(
                                                    peerStates.values(),
                                                    state,
                                                    getBestBlockNumber());
                                } else {
                                    state.incRepeated();
                                    state.setBase(b.getNumber() + batch.size());
                                }
                                break;
                            }
                        case THUNDER:
                            {
                                state.incRepeated();
                                break;
                            }
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
                            state =
                                    forwardModeUpdate(
                                            state, lastBlock, importResult, b.getNumber());
                            break;
                        case LIGHTNING:
                        case THUNDER:
                            state =
                                    attemptLightningJump(
                                            peerStates.values(), state, getBestBlockNumber());
                            break;
                        case NORMAL:
                            state.incRepeated();
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
                                        peerStates.values(), state, getBestBlockNumber());
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

    private ImportResult importBlock(AionBlock b, String displayId, PeerState state) {
        ImportResult importResult;
        long t1 = System.currentTimeMillis();
        importResult = this.chain.tryToConnect(b);
        long t2 = System.currentTimeMillis();
        log.info(
                "<import-status: node = {}, sync mode = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                displayId,
                (state != null ? state.getMode() : NORMAL),
                b.getShortHash(),
                b.getNumber(),
                b.getTransactionsList().size(),
                importResult,
                t2 - t1);
        return importResult;
    }

    private PeerState forwardModeUpdate(
            PeerState state, long lastBlock, ImportResult importResult, long blockNumber) {
        // TODO: check if two long values are needed
        // continue
        state.setBase(lastBlock);
        state.incRepeated();
        // if the imported best block, switch back to normal mode
        if (importResult.isBest()) {
            state.setMode(NORMAL);
            // switch peers to NORMAL otherwise they may never switch back
            for (PeerState peerState : peerStates.values()) {
                if (peerState.getMode() != NORMAL) {
                    peerState.setMode(NORMAL);
                    state.setBase(blockNumber);
                    peerState.resetLastHeaderRequest();
                }
            }
        }
        // if the maximum number of repeats is passed
        // then the peer is stuck endlessly importing old blocks
        // otherwise it would have found an IMPORTED block already
        if (state.isOverRepeatThreshold()) {
            state.setMode(NORMAL);
            state.setBase(getBestBlockNumber());
            state.resetLastHeaderRequest();
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
                batchFromDisk =
                        batchFromDisk
                                .stream()
                                .filter(this::isNotImported)
                                .collect(Collectors.toList());

                if (batchFromDisk.size() > 0) {
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

                            if (last <= b.getNumber()) {
                                // can try importing more
                                last = b.getNumber() + 1;
                            }
                        } else {
                            // do not delete queue from storage
                            importedQueues.remove(entry.getKey());
                            // stop importing this queue
                            break;
                        }
                    } catch (Throwable e) {
                        log.error("<import-block throw> {}", e.toString());
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
            chain.dropImported(level, importedQueues, levelFromDisk);

            // increment level
            level++;
        }

        // switch to NORMAL if in FORWARD mode
        if (importResult.isBest() && state.getMode() == FORWARD) {
            state.setMode(NORMAL);
            state.setBase(getBestBlockNumber());
        }

        return imported;
    }

    private long getBestBlockNumber() {
        return chain.getBestBlock() == null ? 0 : chain.getBestBlock().getNumber();
    }
}
