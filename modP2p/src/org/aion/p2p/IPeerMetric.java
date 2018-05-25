package org.aion.p2p;

public interface IPeerMetric {

    boolean shouldNotConn();

    void incFailedCount();

    void decFailedCount();

    void ban();

    boolean notBan();

}
