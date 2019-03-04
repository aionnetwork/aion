package org.aion.api.server.types;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.interfaces.block.BlockSummary;
import org.aion.interfaces.tx.Transaction;

public abstract class Fltr {

    public static final short EVTS_MAX = 1000;
    public static final long TIMEOUT_MILLIS = 300000l;

    public AtomicLong lastPollTime;

    public enum Type {
        EVENT,
        BLOCK,
        TRANSACTION,
        LOG
    }

    /**
     * because to use this member variable which cause illegalAccessError make sure use synchronized
     * method
     */
    private Type type;

    protected ArrayBlockingQueue<Evt> events = new ArrayBlockingQueue<>(EVTS_MAX);

    public Fltr(final Type _type) {
        this.lastPollTime = new AtomicLong(System.currentTimeMillis());
        this.type = _type;
    }

    public int getSize() {
        return this.events.size();
    }

    public synchronized boolean isFull() {
        return this.events.remainingCapacity() == 0;
    }

    public synchronized Type getType() {
        return this.type;
    }

    public synchronized Object[] poll() {
        // type erasure ... forced to return Object[]
        // downside: user of this class needs to know that internal data-structure it a List of Evt
        // Objects
        Object[] ret = events.toArray();
        this.lastPollTime.set(System.currentTimeMillis());
        this.events.clear();
        return ret;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - this.lastPollTime.get()) > TIMEOUT_MILLIS;
    }

    /*
       The spec provides no guidance on list size or policy on what to do when buffer is full

       Of the following two possible policies, policy A) has been implemented below:
       A) After filling up the events queue, just stop putting elements into the queue on subesquent calls to add()
          onus on user to manually clean out this array.
       B) Keep filling up the queue, ring-buffer style
    */
    public synchronized void add(Evt evt) {
        if (events.size() < EVTS_MAX) events.add(evt);
    }

    public boolean onBlock(BlockSummary b) {
        return false;
    }

    public boolean onTransaction(Transaction tx) {
        return false;
    }
}
