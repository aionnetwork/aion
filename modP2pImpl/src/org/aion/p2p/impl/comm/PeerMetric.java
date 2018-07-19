/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.p2p.impl.comm;

import org.aion.p2p.IPeerMetric;
import org.aion.p2p.P2pConstant;

public final class PeerMetric implements IPeerMetric {

    private int metricFailedConn;
    private long metricFailedConnTs;
    private long metricBanConnTs;

    /**
     * Returns true only if we should not accept any more connections.
     */
    @Override
    public boolean shouldNotConn() {
        return (metricFailedConn > P2pConstant.STOP_CONN_AFTER_FAILED_CONN
                && ((System.currentTimeMillis() - metricFailedConnTs) > P2pConstant.FAILED_CONN_RETRY_INTERVAL))
                || ((System.currentTimeMillis() - metricBanConnTs) < P2pConstant.BAN_CONN_RETRY_INTERVAL);
    }

    /**
     * Increments the failed connection counter.
     */
    @Override
    public void incFailedCount() {
        metricFailedConn++;
        metricFailedConnTs = System.currentTimeMillis();
    }

    /**
     * Decrements the failed connection counter.
     */
    @Override
    public void decFailedCount() {
        if (metricFailedConn > 0)
            metricFailedConn--;
    }

    /**
     * Sets the current time for tracking a banned connection.
     */
    @Override
    public void ban() {
        metricBanConnTs = System.currentTimeMillis();
    }

    /**
     * Returns true only if the time between now and the last ban is greater than the banned
     * connection retry interval.
     */
    @Override
    public boolean notBan() {
        return ((System.currentTimeMillis() - metricBanConnTs) > P2pConstant.BAN_CONN_RETRY_INTERVAL);
    }
}