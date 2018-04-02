package org.aion.zero.impl.tx;

/**
 * Aion Tx Collector
 *
 * Rather than broadcast tx out as soon as they come in; the TxCollector buffers tx and broadcasts them out in batches
 *
 */
public class TxCollector {

    private TxCollector() {
    }

    static private TxCollector instance;

    static public TxCollector getInstance() {
        if (instance == null) {
            instance = new TxCollector();
        }
        return instance;
    }

}
