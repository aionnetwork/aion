package org.aion.p2p.impl.selector;

import org.aion.p2p.impl.ChannelBuffer;
import org.aion.p2p.impl.TaskWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainIOLoop implements Runnable {

    private Selector currSelector;

    private final SelectorProvider selectorProvider;

    private volatile boolean isRunning = false;

    private volatile Thread eventLoopThread;

    private final IOEventBus eventBus = new IOEventBus();

    private final AtomicBoolean wakenUp = new AtomicBoolean();

    private long timeoutMillis = 1000L; // 1s

    private volatile boolean needsToSelectAgain = false;

    // event-bus related

    public MainIOLoop(SelectorProvider selectorProvider) {
        this.selectorProvider = selectorProvider;

        try {
            this.currSelector = selectorProvider.openSelector();
        } catch (IOException e) {
            // failed to create a selector, try again before loop
        }
    }

    // implements the bare minimum required to achieve a stable
    // NIO loop
    @Override
    public void run() {
        registerEventLoopThread(Thread.currentThread());
        this.isRunning = true;
        try {
            this.currSelector = selectorProvider.openSelector();
        } catch (IOException e) {
            // failed to create selector
            return;
        }

        try {
            while(!Thread.currentThread().isInterrupted()) {
                select(this.wakenUp.getAndSet(false));

                // see: <a href="https://github.com/netty/netty/blob/4.1/transport/src/main/java/io/netty/channel/nio/NioEventLoop.java#L411"></a>
                if (this.wakenUp.get())
                    this.currSelector.wakeup();

                try {
                    processSelectedKeys();
                } finally {
                    runAllTasks();
                }
            }
        } catch (Throwable t) {
            // loop should not die for now just log out the throw
        }
    }


    // -------------------------------------------------------------- internal

    private void registerEventLoopThread(Thread t) {
        this.eventLoopThread = t;
    }


    private void select(boolean oldWakenUp) throws IOException {
        Selector selector = this.currSelector;

        while (true) {

            // from netty docs:
            // If a task was submitted when wakenUp value was true, the task didn't get a chance to call
            // {@link Selector#wakeup}. So we need to check task queue again before executing select operation.
            // If we don't, the task might be pended until select operation was timed out.
            // It might be pended until idle timeout if IdleStateHandler existed in pipeline.
            // -- end netty notes
            if (this.eventBus.hasTasks() && wakenUp.compareAndSet(false, true)) {
                selector.selectNow();
                break;
            }

            int selectedKeys = selector.select(timeoutMillis);

            if (selectedKeys != 0 || oldWakenUp || wakenUp.get() || this.eventBus.hasTasks()) {
                // break when we:
                // 1) selected something
                // 2) user submitted a task for us to run
                // 3) the task queue has an already pending task
                break;
            }

            if (Thread.interrupted()) {
                break;
            }
        }

        // TODO: handle spin lock (epoll error)
        // see: <a href="https://github.com/netty/netty/blob/4.1/transport/src/main/java/io/netty/channel/nio/NioEventLoop.java#L738"></a>
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = this.currSelector.selectedKeys();
        if (selectedKeys.isEmpty())
            return;

        Iterator<SelectionKey> it = selectedKeys.iterator();
        while (true) {
            final SelectionKey key = it.next();
            final ChannelBuffer buffer = (ChannelBuffer) key.attachment();
            processSelectedKey(key, buffer);
            // remove the current key
            it.remove();

            if (!it.hasNext())
                    break;

            if (this.needsToSelectAgain) {
                selectAgain();
                Set<SelectionKey> keys = this.currSelector.selectedKeys();
                if (keys.isEmpty())
                    break;
                else
                    it = selectedKeys.iterator();
            }
        }
    }

    private static void processSelectedKey(SelectionKey key, ChannelBuffer buffer) {
        Task t = buffer.task;
        int state = 0;
        try {
            t.channelReady(key.channel(), key);
            state = 1;
        } catch (Exception e) {
            // on any exception, drop
            key.cancel();
            t.channelUnregistered(key.channel(), e);
            state = 2;
        } finally {
            // this should not happen, but handle anyways
            if (state == 0) {
                key.cancel();
                t.channelUnregistered(key.channel(), null);
            } else if (state == 1) {
                // if the task cancelled the key
                if (!key.isValid())
                    t.channelUnregistered(key.channel(), null);
            }
        }
    }

    private boolean runAllTasks() {
        assert isEventLoopThread();
        List<Runnable> tasks = this.eventBus.retrieveAllEvents();

        if (tasks.isEmpty())
            return false;

        for (Runnable task : tasks) {
            safeExecute(task);
        }
        return true;
    }

    private void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void selectAgain() {
        this.needsToSelectAgain = false;
        try {
            this.currSelector.selectNow();
        } catch (IOException e) {
            // TODO: this needs formatting
            e.printStackTrace();
        }
    }

    private void wakeup(boolean inEventLoop) {
        if (!inEventLoop && this.wakenUp.compareAndSet(false, true))
            this.currSelector.wakeup();
    }

    // -------------------------------------------------------------- public

    public boolean isEventLoopThread() {
        return Thread.currentThread() == this.eventLoopThread;
    }

    public void cancel(SelectionKey key) {
        key.cancel();
        this.needsToSelectAgain = true;
    }

    /**
     * Called to attach a new channel to the event loop
     *
     * @param channel a selectable channel that is active
     * @param buffer associated buffer attached with channel
     */
    public void attachChannel(SelectableChannel channel, int interestOps, ChannelBuffer buffer, Task task) {
        if (channel == null)
            throw new NullPointerException();

        if (interestOps == 0)
            throw new IllegalArgumentException();

        if ((interestOps & ~channel.validOps()) != 0) {
            throw new IllegalArgumentException();
        }

        if (buffer == null)
            throw new NullPointerException();

        if (task == null)
            throw new NullPointerException();

        // just in case the user forgets to set it
        if (buffer.task == null)
            buffer.task = task;

        // schedule an event for the channel to be attached
        this.eventBus.addEvent(() -> {
            try {
                SelectionKey key = channel.register(this.currSelector, interestOps, buffer);
                key.attach(buffer);
            } catch (ClosedChannelException e) {
                buffer.task.channelUnregistered(channel, e);
            }
        });
        wakeup(isEventLoopThread());
    }

    /**
     * Submit a new task (this is a write task the base class to serialize messages)
     */
    public void write(ByteBuffer buffer, SocketChannel channel) {
        this.eventBus.addEvent(() -> {
            try {
                if (!channel.isOpen()) {
                    System.out.println("could not write, channel was closed");
                    return;
                }

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            } catch (IOException e) {
                System.out.println("could not write to buffer");
            }
        });
    }

    public SelectorProvider getSelectorProvider() {
        return this.selectorProvider;
    }

    public void cancelChannel(SocketChannel channel) {
        this.eventBus.addEvent(() -> {
            SelectionKey key = channel.keyFor(this.currSelector);
            if (key != null)
                key.cancel();
        });
    }
}
