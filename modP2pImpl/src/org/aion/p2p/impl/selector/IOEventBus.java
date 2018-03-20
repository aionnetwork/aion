package org.aion.p2p.impl.selector;

import java.nio.channels.SelectableChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class IOEventBus {
    private Queue<Runnable> queue;

    public IOEventBus() {
        this.queue = new LinkedBlockingQueue<>();
    }

    public void addEvent(Runnable run) {
        this.queue.offer(run);
    }

    public List<Runnable> retrieveAllEvents() {
        List<Runnable> runnables = Arrays.asList(this.queue.toArray());
    }

    public boolean hasTasks() {
        return !this.queue.isEmpty();
    }
}
