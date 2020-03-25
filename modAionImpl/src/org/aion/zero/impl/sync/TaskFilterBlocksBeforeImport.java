package org.aion.zero.impl.sync;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.sync.statistics.BlockType;
import org.slf4j.Logger;

/**
 * Filters received blocks by delegating the ones far in the future to storage and delaying queue
 * population when the predefined capacity is reached.
 *
 * @author Alexandra Roatis
 */
final class TaskFilterBlocksBeforeImport implements Runnable {

    private static final int MIN_STORAGE_DIFF = 10;
    private static final int MAX_STORAGE_DIFF = 200; // should be lower than SyncHeaderRequestManager.MAX_BLOCK_DIFF
    private static final int PREFERRED_QUEUE_SIZE = 100;
    private static final long SLEEP_DURATION_MS = 1_000L; // = 1 sec

    private final AionBlockchainImpl chain;
    private final AtomicBoolean start;

    private final BlockingQueue<BlocksWrapper> downloadedBlocks;
    private final PriorityBlockingQueue<BlocksWrapper> sortedBlocks;

    private final SyncStats syncStats;

    private final Logger log;
    private final Logger surveyLog;

    TaskFilterBlocksBeforeImport(
            final Logger syncLog,
            final Logger surveyLog,
            final AionBlockchainImpl chain,
            final AtomicBoolean start,
            final SyncStats syncStats,
            final BlockingQueue<BlocksWrapper> downloadedBlocks,
            final PriorityBlockingQueue<BlocksWrapper> sortedBlocks) {
        this.log = syncLog;
        this.surveyLog = surveyLog;
        this.chain = chain;
        this.start = start;
        this.syncStats = syncStats;
        this.downloadedBlocks = downloadedBlocks;
        this.sortedBlocks = sortedBlocks;
    }

    @Override
    public void run() {
        // for runtime survey information
        long startTime, duration;
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        try {
            while (start.get()) {
                startTime = System.nanoTime();
                BlocksWrapper bw = downloadedBlocks.take();
                duration = System.nanoTime() - startTime;
                surveyLog.debug("Import Stage 1.A: wait for downloaded blocks, duration = {} ns.", duration);

                long currentBest = getBestBlockNumber();
                boolean isFarInFuture = bw.firstBlockNumber > currentBest + MAX_STORAGE_DIFF;
                boolean isRestrictedCapacity = (sortedBlocks.size() >= PREFERRED_QUEUE_SIZE)
                                            && (bw.firstBlockNumber > currentBest + MIN_STORAGE_DIFF);

                if (isFarInFuture || isRestrictedCapacity) {
                    int stored = chain.storePendingBlockRange(bw.blocks, log);
                    syncStats.updatePeerBlocks(bw.displayId, stored, BlockType.STORED);
                } else {
                    // unfortunately the PriorityBlockingQueue does not support a bounded size
                    // therefore this while mimics blocking when the queue is full
                    while (sortedBlocks.size() >= PREFERRED_QUEUE_SIZE) {
                        Thread.sleep(SLEEP_DURATION_MS);
                    }
                    sortedBlocks.put(bw);
                }
            }
        } catch (InterruptedException e) {
            if (start.get()) {
                log.error("Thread interrupted without shutdown request.", e);
            }
        } finally {
            if (start.get()) {
                throw new IllegalThreadStateException("Thread terminated without shutdown request.");
            }
        }
    }

    private long getBestBlockNumber() {
        return chain.getBestBlock() == null ? 0 : chain.getBestBlock().getNumber();
    }
}
