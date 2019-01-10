package org.aion.p2p.impl.comm;

import org.aion.p2p.IPeerMetric;
import org.aion.p2p.P2pConstant;

public final class PeerMetric implements IPeerMetric {

    private int metricFailedConn;
    private long metricFailedConnTs;
    private long metricBanConnTs;
    private final int banInterval;

    PeerMetric() {
        banInterval = P2pConstant.BAN_CONN_RETRY_INTERVAL;
    }

    /*
     * Currently for shorten the testing time
     */
    PeerMetric(int _bi) {
        if (_bi < P2pConstant.FAILED_CONN_RETRY_INTERVAL || _bi > 86_400_000) {
            banInterval = P2pConstant.BAN_CONN_RETRY_INTERVAL;
        } else {
            banInterval = _bi;
        }
    }

    /*
     * Returns true only if we should not accept any more connections.
     */
    @Override
    public boolean shouldNotConn() {
        return (metricFailedConn > P2pConstant.STOP_CONN_AFTER_FAILED_CONN
                        && ((System.currentTimeMillis() - metricFailedConnTs)
                                < P2pConstant.FAILED_CONN_RETRY_INTERVAL))
                || !notBan();
    }

    /** Increments the failed connection counter. */
    @Override
    public void incFailedCount() {
        metricFailedConn++;
        metricFailedConnTs = System.currentTimeMillis();
    }

    /*
     * Decrements the failed connection counter.
     */
    @Override
    public void decFailedCount() {
        if (metricFailedConn > 0) {
            metricFailedConn--;
        }
    }

    /*
     * Sets the current time for tracking a banned connection.
     */
    @Override
    public void ban() {
        metricBanConnTs = System.currentTimeMillis();
    }

    /*
     * Returns true only if the time between now and the last ban is greater than the banned
     * connection retry interval.
     */
    @Override
    public boolean notBan() {
        return ((System.currentTimeMillis() - metricBanConnTs) > getBanInterval());
    }

    int getBanInterval() {
        return banInterval;
    }
}
