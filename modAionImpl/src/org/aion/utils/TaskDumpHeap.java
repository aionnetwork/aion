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

    public TaskDumpHeap(
            final AtomicBoolean _start, final int _interval, final String _reportFolder) {
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
