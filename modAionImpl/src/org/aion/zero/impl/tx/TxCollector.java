package org.aion.zero.impl.tx;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.types.AionTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Aion Tx Collector
 *
 * Rather than broadcast tx out as soon as they come in; the TxCollector buffers tx and broadcasts them out in batches
 *
 */

public class TxCollector {

    // Average TxSize bytes
    private final int avgTxSize = 200;

    // Average number of Tx per batch
    private final int avgNumTxBatch = 100;

    // Soft cap max bytes per batch
    private final int maxTxBufferSize = avgTxSize * avgNumTxBatch;
    private final int offerTimeout = 100;

    // Max time between sending Tx if present in queue (in ms)
    private final int maxDelay = 5000;

    private IP2pMgr p2p;

    private AtomicInteger queueSizeBytes;
    private AtomicLong lastBroadcast;
    private LinkedBlockingQueue<AionTransaction> transactionQueue;

    // Executor service to collect and broadcast txs
    private final ScheduledExecutorService broadcastTx;
    private final int initDelay = 10; //Seconds till start broadcast
    private final int broadcastLoop = 5; //Time between broadcasting tx


    public TxCollector(IP2pMgr p2p) {
        this.p2p = p2p;
        this.queueSizeBytes.set(0);
        this.lastBroadcast.set(System.currentTimeMillis());

        // Leave unbounded for now, may need to restrict queue size and drop tx until able to process tx
        transactionQueue = new LinkedBlockingQueue<>();

        broadcastTx = Executors.newSingleThreadScheduledExecutor();
        broadcastTx.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                broadcastTransactionsTask();
            }
        }, initDelay, broadcastLoop, TimeUnit.SECONDS);

    }

    /*
    * Submit a batch list of tx
     */
    public void submitTx(List<AionTransaction> txs) {
        // addAll potentially dangerous for blocking queue, add manually
        for(AionTransaction tx : txs) {
            try {
                transactionQueue.offer(tx, offerTimeout, TimeUnit.MILLISECONDS);
                queueSizeBytes.getAndAdd(tx.getEncoded().length);
            } catch (InterruptedException e) {
                // Interrupted, no problem
            }
        }
    }

    /*
     * Submit a single Tx
     */
    public void submitTx(AionTransaction tx) {
        try {
            transactionQueue.offer(tx, offerTimeout, TimeUnit.MILLISECONDS);
            queueSizeBytes.getAndAdd(tx.getEncoded().length);
        } catch (InterruptedException e) {
            // Interrupted, no problem
        }
    }

    /*
    Preemptively called by submit methods when queue size hits defined limits
     */
    private void broadcastTransactions() {
        int currentSize = 0;
        List<AionTransaction> txToSend = new ArrayList<>();

        transactionQueue.drainTo(txToSend);

    }

    /*
    Run periodically by scheduled executor to ensure tasks will be sent out in timely fashion
     */
    private void broadcastTransactionsTask() {
        int currentSize = 0;
        List<AionTransaction> txToSend = new ArrayList<>();
        int numTxInBatch = 0;

        // Return if preemptively send txs already
        if(System.currentTimeMillis() - lastBroadcast.get() < this.maxDelay) {
            return;
        }

        while(transactionQueue.size() > 0 && transactionQueue.peek().getEncoded().length + currentSize < maxTxBufferSize
                && numTxInBatch < avgNumTxBatch) {
            try {
                AionTransaction tx = transactionQueue.take();
                txToSend.add(tx);
                queueSizeBytes.addAndGet(tx.getEncoded().length * -1); // Decrease bytes in queue
            } catch (InterruptedException e) {
                // Interrupted while waiting, no problem
            }
        }

        // Send transaction
        if(txToSend.size() > 0) {
            A0TxTask txTask = new A0TxTask(txToSend, this.p2p);
            TxBroadcaster.getInstance().submitTransaction(txTask);
        }

        this.lastBroadcast.set(System.currentTimeMillis());
    }
}
