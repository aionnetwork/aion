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
import java.util.concurrent.locks.ReentrantLock;

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

    private IP2pMgr p2p;

    private AtomicInteger queueSizeBytes = new AtomicInteger();
    private AtomicLong lastBroadcast = new AtomicLong(System.currentTimeMillis());
    private LinkedBlockingQueue<AionTransaction> transactionQueue;

    private ReentrantLock broadcastLock = new ReentrantLock();


    public TxCollector(IP2pMgr p2p) {
        this.p2p = p2p;

        // Leave unbounded for now, may need to restrict queue size and drop tx until able to process tx
        transactionQueue = new LinkedBlockingQueue<>();

        ScheduledExecutorService broadcastTxExec = Executors.newSingleThreadScheduledExecutor();
        int broadcastLoop = 1;
        int initDelay = 10;
        broadcastTxExec.scheduleAtFixedRate(this::broadcastTransactionsTask, initDelay, broadcastLoop, TimeUnit.SECONDS);

    }

    /*
    * Submit a batch list of tx
     */
    public void submitTx(List<AionTransaction> txs) {
        // addAll potentially dangerous for blocking queue, add manually
        for(AionTransaction tx : txs) {
            try {
                transactionQueue.offer(tx, offerTimeout, TimeUnit.MILLISECONDS);
                if (queueSizeBytes.addAndGet(tx.getEncoded().length) >= this.maxTxBufferSize)
                    broadcastTx();

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
            if (queueSizeBytes.addAndGet(tx.getEncoded().length) >= this.maxTxBufferSize)
                broadcastTx();

        } catch (InterruptedException e) {
            // Interrupted, no problem
        }
    }

    private void broadcastTx() {

        List<AionTransaction> transactions;
        broadcastLock.lock();
        try {

            // Check tx queue has not already been emptied
            if(transactionQueue.isEmpty())
                return;

            // Grab everything in the queue
            transactions = new ArrayList<>(transactionQueue.size());
            transactionQueue.drainTo(transactions);

            //Reduce counter
            for(AionTransaction a : transactions) {
                queueSizeBytes.addAndGet(a.getEncoded().length * -1);
            }
        }finally {
            broadcastLock.unlock();
        }

        // Update last broadcast time
        this.lastBroadcast.set(System.currentTimeMillis());

        if (!transactions.isEmpty()) {
            TxBroadcaster.getInstance().submitTransaction(new A0TxTask(transactions, this.p2p));
        }
    }

    /*
    Run periodically by scheduled executor to ensure tasks will be sent out in timely fashion
     */
    private void broadcastTransactionsTask() {
        int maxDelay = 1000;
        if(System.currentTimeMillis() - this.lastBroadcast.get() < maxDelay)
            return;

        broadcastTx();
    }
}