/*******************************************************************************
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
 ******************************************************************************/
package org.aion.utils;

import org.aion.zero.impl.db.AionBlockStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread for printing heap dumps.
 *
 * @author Alexandra Roatis
 */
public class TaskDumpThreadsAndBlocks implements Runnable {

    private final AtomicBoolean start;
    private final int interval;

    private final String reportFolder;

    private final AionBlockStore store;
    private final int blockCount;

    public TaskDumpThreadsAndBlocks(final AtomicBoolean _start, final int _interval, final AionBlockStore _store,
            final int _blockCount, final String _reportFolder) {
        this.start = _start;
        this.interval = _interval;
        this.store = _store;
        this.blockCount = _blockCount;
        this.reportFolder = _reportFolder;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        while (this.start.get()) {

            // printing threads
            try {
                Files.write(Paths.get(reportFolder, System.currentTimeMillis() + "-thread-report.out"),
                        ThreadDumper.dumpThreadInfo().getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // printing blocks
            try {
                store.dumpPastBlocks(blockCount, reportFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}

