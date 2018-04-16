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

import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.ImportResult;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author chris
 * handle process of importing blocks to repo
 * TODO: targeted send
 */
final class TaskImportBlocks implements Runnable {

    private final IP2pMgr p2p;

    private final AionBlockchainImpl chain;

    private final AtomicBoolean start;

    private final BlockingQueue<BlocksWrapper> downloadedBlocks;

    private final SyncStatics statis;

    private final Map<ByteArrayWrapper, Object> importedBlockHashes;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    TaskImportBlocks(
            final IP2pMgr p2p,
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final SyncStatics _statis,
            final BlockingQueue<BlocksWrapper> downloadedBlocks,
            final Map<ByteArrayWrapper, Object> importedBlockHashes,
            final Map<Integer, PeerState> peerStates,
            final Logger log
    ) {
        this.p2p = p2p;
        this.chain = _chain;
        this.start = _start;
        this.statis = _statis;
        this.downloadedBlocks = downloadedBlocks;
        this.importedBlockHashes = importedBlockHashes;
        this.peerStates = peerStates;
        this.log = log;
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

            List<AionBlock> batch = bw.getBlocks().stream()
                    .filter(b -> importedBlockHashes.get(ByteArrayWrapper.wrap(b.getHash())) == null)
                    .collect(Collectors.toList());

            PeerState state = peerStates.get(bw.getNodeIdHash());
            if (state == null) {
                log.warn("This is not supposed to happen, but the peer is sending us blocks without ask");
            }

            for (AionBlock b : batch) {
                long t1 = System.currentTimeMillis();
                ImportResult importResult;
                try {
                    importResult = this.chain.tryToConnect(b);
                } catch (Throwable e) {
                    log.error("<import-block throw> {}", e.toString());
                    if (e.getMessage().contains("No space left on device")) {
                        log.error("Shutdown due to lack of disk space.");
                        System.exit(0);
                    }
                    continue;
                }
                long t2 = System.currentTimeMillis();
                log.info("<import-status: node = {}, hash = {}, number = {}, txs = {}, result = {}, time elapsed = {} ms>",
                        bw.getDisplayId(),
                        b.getShortHash(),
                        b.getNumber(),
                        b.getTransactionsList().size(),
                        importResult,
                        t2 - t1);
                switch (importResult) {
                    case IMPORTED_BEST:
                    case IMPORTED_NOT_BEST:
                    case EXIST:
                        importedBlockHashes.put(ByteArrayWrapper.wrap(b.getHash()), true);
                        break;
                    default:
                        break;
                }

                // decide whether to change mode based on the first
                if (b == batch.get(0) && state != null) {

                    PeerState.Mode mode = state.getMode();

                    switch (importResult) {
                        case IMPORTED_BEST:
                        case IMPORTED_NOT_BEST:
                        case EXIST:
                            // assuming the remaining blocks will be imported. if not, the state
                            // and base will be corrected by the next cycle
                            long lastBlock = batch.get(batch.size() - 1).getNumber();

                            if (mode == PeerState.Mode.BACKWARD) {
                                // we found the fork point
                                state.setMode(PeerState.Mode.FORWARD);
                                state.setBase(lastBlock);

                            } else if (mode == PeerState.Mode.FORWARD) {
                                // continue
                                state.setBase(lastBlock);
                                // if the imported best block, switch back to normal mode
                                if (importResult != ImportResult.IMPORTED_NOT_BEST) {
                                    state.setMode(PeerState.Mode.NORMAL);
                                }
                            }
                            break;
                        case NO_PARENT:
                            if (mode == PeerState.Mode.BACKWARD) {
                                // update base
                                state.setBase(b.getNumber());
                            } else {
                                // switch to backward mode
                                state.setMode(PeerState.Mode.BACKWARD);
                                state.setBase(b.getNumber());
                            }
                            break;
                    }
                }
            }

            state.resetLastHeaderRequest(); // so we can continue immediately

            this.statis.update(this.chain.getBestBlock().getNumber());
        }
    }
}
