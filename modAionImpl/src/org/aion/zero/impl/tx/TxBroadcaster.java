package org.aion.zero.impl.tx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.aion.base.AionTransaction;
import org.aion.mcf.tx.AbstractTxTask;

@SuppressWarnings("rawtypes")
public class TxBroadcaster<TXTASK extends AbstractTxTask> {

    private TxBroadcaster() {}

    private static class Holder {
        static final TxBroadcaster INSTANCE = new TxBroadcaster();
    }

    public static TxBroadcaster getInstance() {
        return Holder.INSTANCE;
    }

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    @SuppressWarnings("unchecked")
    public Future<List<AionTransaction>> submitTransaction(TXTASK task) {
        return executor.submit(task);
    }
}
