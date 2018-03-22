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

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread for printing heap dumps.
 *
 * @author Alexandra Roatis
 */
public class TaskDumpHeap implements Runnable {

    private final AtomicBoolean start;
    private final int interval;

    private final String reportFolder;

    public TaskDumpHeap(final AtomicBoolean _start, final int _interval, final String _reportFolder) {
        this.start = _start;
        this.interval = _interval;
        this.reportFolder = _reportFolder;
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (this.start.get()) {

            File file = new File(reportFolder, System.currentTimeMillis() + "-heap-report.hprof");

            HeapDumper.dumpHeap(file.getAbsolutePath(), true);

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}

