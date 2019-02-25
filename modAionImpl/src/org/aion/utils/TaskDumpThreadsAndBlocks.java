package org.aion.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.zero.impl.db.AionBlockStore;

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

    public TaskDumpThreadsAndBlocks(
            final AtomicBoolean _start,
            final int _interval,
            final AionBlockStore _store,
            final int _blockCount,
            final String _reportFolder) {
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
                Files.write(
                        Paths.get(reportFolder, System.currentTimeMillis() + "-thread-report.out"),
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
