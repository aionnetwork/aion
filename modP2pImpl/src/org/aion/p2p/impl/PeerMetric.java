package org.aion.p2p.impl;

public class PeerMetric {

    public static final int STOP_CONN_AFTER_FAILED_CONN = 3;
    int metricFailedConn;

    boolean shouldNotConn() {
        return metricFailedConn > STOP_CONN_AFTER_FAILED_CONN;
    }

    void incFailedCount() {
        metricFailedConn++;
    }

    void decFailedCount() {
        if (metricFailedConn > 0)
            metricFailedConn--;
    }

}
