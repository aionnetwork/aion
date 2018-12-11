package org.aion.p2p;

/** An interface for tracking peer connection and banning metrics. */
public interface IPeerMetric {

    /** Returns true only if we should not accept any more connections. */
    boolean shouldNotConn();

    /** Increments the failed connection counter. */
    void incFailedCount();

    /** Decrements the failed connection counter. */
    void decFailedCount();

    /** Sets the current time for tracking a banned connection. */
    void ban();

    /**
     * Returns true only if the time between now and the last ban is greater than the banned
     * connection retry interval.
     */
    boolean notBan();
}
