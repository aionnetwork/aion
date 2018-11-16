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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Deletes a batch of blocks that was already imported from storage.
 *
 * @author Alexandra Roatis
 */
final class TaskDropImportedBlocks implements Runnable {

    private final AionBlockchainImpl chain;
    private final long level;
    private final List<ByteArrayWrapper> importedQueues;
    private final Map<ByteArrayWrapper, List<AionBlock>> levelFromDisk;

    private final Logger log;

    TaskDropImportedBlocks(
            final AionBlockchainImpl _chain,
            final long _level,
            final List<ByteArrayWrapper> _importedQueues,
            final Map<ByteArrayWrapper, List<AionBlock>> _levelFromDisk,
            final Logger _log) {
        this.chain = _chain;
        this.level = _level;
        this.importedQueues = _importedQueues;
        this.levelFromDisk = _levelFromDisk;
        this.log = _log;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);
        Thread.currentThread().setName("sync-drop:" + level);

        chain.dropImported(level, importedQueues, levelFromDisk);

        // log operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Dropped from storage level = {} with queues = {}.",
                    level,
                    Arrays.toString(importedQueues.toArray()));
        }
    }
}
