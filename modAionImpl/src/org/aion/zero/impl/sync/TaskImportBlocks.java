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
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author chris
 * handle process of importing blocks to repo
 * long run
 */
final class TaskImportBlocks implements Runnable {

    private final SyncMgr sync;

    private final AionBlockchainImpl chain;

    private final AtomicBoolean start;

    private final AtomicLong jump;

    private final BlockingQueue<AionBlock> importedBlocks;

    private final Map<ByteArrayWrapper, Object> cachedHashes;

    private final Logger log;

    TaskImportBlocks(
            final SyncMgr _sync,
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final AtomicLong _jump,
            final BlockingQueue<AionBlock> _importedBlocks,
            final Map<ByteArrayWrapper, Object> _cachedHashes,
            final Logger _log
    ){
        this.sync = _sync;
        this.chain = _chain;
        this.start = _start;
        this.jump = _jump;
        this.importedBlocks = _importedBlocks;
        this.cachedHashes = _cachedHashes;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        Thread.currentThread().setName("sync-ib");
        while (start.get()) {
            try {
                long start = System.currentTimeMillis();
                long blockNumberIndex = 0;

                List<AionBlock> batch = new ArrayList<>();

                while ((System.currentTimeMillis() - start) < 30) {

                    AionBlock b = importedBlocks.poll(1, TimeUnit.MILLISECONDS);

                    // continue on batched blocks
                    if (b == null) {
                        continue;
                    }

                    // break if start of next batch
                    if (blockNumberIndex > 0 && b.getNumber() != (blockNumberIndex + 1)) {
                        start = 0;
                        continue;
                    }

                    //b = importedBlocks.take();
                    blockNumberIndex = b.getNumber();
                    ByteArrayWrapper hash = new ByteArrayWrapper(b.getHash());

                    if (!cachedHashes.containsKey(hash))
                        batch.add(b);
                }

                // sleep if no batch empty then continue
                if (batch.size() == 0) {
                    Thread.sleep(5000);
                    continue;
                }

                boolean fetchAheadTriggerUsed = false;

                for (AionBlock b : batch) {
                    ImportResult importResult = this.chain.tryToConnect(b);
                    switch (importResult) {
                        case IMPORTED_BEST:
                            if (log.isInfoEnabled()) {
                                log.info("<import-best num={} hash={} txs={}>", b.getNumber(), b.getShortHash(),
                                        b.getTransactionsList().size());
                            }

                            // re-targeting for next batch blocks headers
                            if (!fetchAheadTriggerUsed) {
                                jump.set(batch.get(batch.size() - 1).getNumber());
                                fetchAheadTriggerUsed = true;
                                this.sync.getHeaders();
                            }

                            synchronized (cachedHashes){
                                cachedHashes.put(ByteArrayWrapper.wrap(b.getHash()), null);
                            }
                            break;
                        case IMPORTED_NOT_BEST:
                            if (log.isInfoEnabled()) {
                                log.info("<import-not-best num={} hash={} txs={}>", b.getNumber(), b.getShortHash(),
                                        b.getTransactionsList().size());
                            }

                            synchronized (cachedHashes){
                                cachedHashes.put(ByteArrayWrapper.wrap(b.getHash()), null);
                            }
                            break;
                        case EXIST:
                            // still exist
                            if (log.isDebugEnabled()) {
                                log.debug("<import-fail err=block-exit num={} hash={} txs={}>", b.getNumber(),
                                        b.getShortHash(), b.getTransactionsList().size());
                            }

                            synchronized (cachedHashes){
                                cachedHashes.put(ByteArrayWrapper.wrap(b.getHash()), null);
                            }
                            break;
                        case NO_PARENT:
                            if (log.isDebugEnabled()) {
                                log.debug("<import-fail err=no-parent num={} hash={}>", b.getNumber(), b.getShortHash());
                            }

                            jump.set(jump.get() - 128);
                            continue;
                        case INVALID_BLOCK:
                            if (log.isDebugEnabled()) {
                                log.debug("<import-fail err=invalid-block num={} hash={} txs={}>", b.getNumber(),
                                        b.getShortHash(), b.getTransactionsList().size());
                            }
                            break;
                        default:
                            if (log.isDebugEnabled()) {
                                log.debug("<import-res-unknown>");
                            }
                            break;
                    }
                }
            } catch (Exception ex) {
                return;
            }
        }
    }
}
