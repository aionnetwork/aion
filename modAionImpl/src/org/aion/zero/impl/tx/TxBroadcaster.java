package org.aion.zero.impl.tx;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TxBroadcaster {

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    TxBroadcaster() {}

    void submitTransaction(A0TxTask task) {
        executor.submit(task);
    }
}
