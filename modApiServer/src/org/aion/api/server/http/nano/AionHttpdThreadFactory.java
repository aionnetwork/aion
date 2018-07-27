package org.aion.api.server.http.nano;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author chris lin
 */
public class AionHttpdThreadFactory implements ThreadFactory {
    private final AtomicInteger tnum = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "rpc-worker-" + tnum.getAndIncrement());
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }
}