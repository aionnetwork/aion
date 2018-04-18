package org.aion.p2p.impl2.selector;

import java.nio.channels.SelectableChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class IOEventBus {
    private Queue<Runnable> queue;

    private static final int MAX_PENDING_TASKS = 16;

    public IOEventBus() {
        this.queue = new LinkedBlockingQueue<>();

    }

    public void addEvent(Runnable run) {
        this.queue.offer(run);
    }

    public List<Runnable> retrieveAllEvents() {
        List<Runnable> taskList = new LinkedList<>();
        int i = 0;
        while(!this.queue.isEmpty() && i < MAX_PENDING_TASKS) {
            taskList.add(this.queue.poll());
            i++;
        }
        return taskList;
    }

    public boolean hasTasks() {
        return !this.queue.isEmpty();
    }
}
