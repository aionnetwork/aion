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

import java.util.List;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Places a batch of blocks into storage for later imports.
 *
 * @author Alexandra Roatis
 */
final class TaskStorePendingBlocks implements Runnable {

    private final AionBlockchainImpl chain;
    private final List<AionBlock> batch;
    private final String displayId;
    private final SyncStats stats;
    private final Logger log;

    TaskStorePendingBlocks(
            final AionBlockchainImpl _chain,
            final List<AionBlock> _batch,
            final String _displayId,
            final SyncStats _stats,
            final Logger _log) {
        this.chain = _chain;
        this.batch = _batch;
        this.displayId = _displayId;
        this.stats = _stats;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);
        AionBlock first = batch.get(0);
        Thread.currentThread().setName("sync-save:" + first.getNumber());

        int stored = chain.storePendingBlockRange(batch);
        this.stats.updatePeerStoredBlocks(displayId, stored);

        // log operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Stored {} out of {} blocks starting at hash = {}, number = {} from node = {}.",
                    stored,
                    batch.size(),
                    first.getShortHash(),
                    first.getNumber(),
                    displayId);
        }
    }
}
