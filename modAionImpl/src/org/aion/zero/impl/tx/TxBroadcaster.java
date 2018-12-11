package org.aion.zero.impl.tx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.aion.mcf.tx.AbstractTxTask;
import org.aion.mcf.types.AbstractTransaction;

@SuppressWarnings("rawtypes")
public class TxBroadcaster<TX extends AbstractTransaction, TXTASK extends AbstractTxTask> {

    private TxBroadcaster() {}

    private static class Holder {
        static final TxBroadcaster INSTANCE = new TxBroadcaster();
    }

    public static TxBroadcaster getInstance() {
        return Holder.INSTANCE;
    }

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    @SuppressWarnings("unchecked")
    public Future<List<TX>> submitTransaction(TXTASK task) {
        return executor.submit(task);
    }
}
