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

import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris
 * handle process of importing blocks to repo
 * TODO: targeted send
 */
final class TaskImportBlocks implements Runnable {

    private final AionBlockchainImpl chain;

    private final AtomicBoolean start;

    private final BlockingQueue<BlocksWrapper> importedBlocks;

    private final SyncStatics statis;

    private final Logger log;

    TaskImportBlocks(
            final AionBlockchainImpl _chain,
            final AtomicBoolean _start,
            final BlockingQueue<BlocksWrapper> _importedBlocks,
            final SyncStatics _statis,
            final Logger _log
    ){
        this.chain = _chain;
        this.start = _start;
        this.importedBlocks = _importedBlocks;
        this.statis = _statis;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (start.get()) {

            BlocksWrapper bw;
            try {
                bw = importedBlocks.take();
            } catch (InterruptedException ex) {
                return;
            }

            List<AionBlock> batch = bw.getBlocks();
            for (AionBlock b : batch) {
                ImportResult importResult = this.chain.tryToConnect(b);
                switch (importResult) {
                    case IMPORTED_BEST:
                        if (log.isInfoEnabled()) {
                            log.info("<import-best num={} hash={} txs={}>", b.getNumber(), b.getShortHash(),
                                    b.getTransactionsList().size());
                        }

                        break;
                    case IMPORTED_NOT_BEST:
                        if (log.isInfoEnabled()) {
                            log.info("<import-not-best from-node={} num={} hash={} txs={}>", bw.getDisplayId(), b.getNumber(), b.getShortHash(),
                                    b.getTransactionsList().size());
                        }

                        break;
                    case EXIST:
                        if (log.isDebugEnabled()) {
                            log.debug("<import-fail err=block-exit num={} hash={} txs={}>", b.getNumber(),
                                    b.getShortHash(), b.getTransactionsList().size());
                        }

                        break;
                    case NO_PARENT:
                        if (log.isDebugEnabled()) {
                            log.debug("<import-fail err=no-parent from-node={} num={} hash={}>", bw.getDisplayId(), b.getNumber(), b.getShortHash());
                        }

                        break;
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
            this.statis.update(this.chain.getBestBlock().getNumber());
        }
    }
}
