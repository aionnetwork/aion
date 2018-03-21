package org.aion.api.server.http;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author chris
 */
class RpcThreadFactory implements ThreadFactory {
    // TODO: for debugging. remove in production
    private final AtomicInteger tnum = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, "rpc-worker-" + tnum.getAndIncrement());
    }
}