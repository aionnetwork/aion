package org.aion.base.timer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Modified to use caching thread pools instead
 *
 * <p>Note: cannot be shared between threads
 *
 * @author yao
 */
public class StackTimer implements ITimer {

    public static int NANO_INTERVAL = 10;

    // private final TimerThread thread = new TimerThread(stack);
    private final StackTimerRunnable timerRunnable;

    /**
     * We won't shutdown the thread pool until the very end, (when we need to shutdown the program)
     */
    private static final ExecutorService timers =
            Executors.newCachedThreadPool(
                    new ThreadFactory() {

                        @Override
                        public Thread newThread(Runnable arg0) {
                            Thread thread = new Thread(arg0, "StackTimer");
                            thread.setPriority(Thread.MAX_PRIORITY);
                            return thread;
                        }
                    });

    /** Called upon program exit, to shutdown the thread pool */
    public static void shutdownPool() {
        timers.shutdown();
    }

    public StackTimer() {
        timerRunnable = new StackTimerRunnable();
        timers.execute(timerRunnable);
    }

    @Override
    public void sched(TimerTask task) {
        if (task == null) {
            throw new RuntimeException("task cannot be null");
        }

        if (!(task.getTimeout() > 0)) {
            throw new RuntimeException("timeout has to be > 0");
        }
        task.start();
        timerRunnable.submit(task);
    }

    @Override
    public void shutdown() {
        timerRunnable.shutdown();
    }

    public boolean completed() {
        return timerRunnable.done();
    }

    protected StackTimerRunnable getTimerRunnable() {
        return this.timerRunnable;
    }
}
