/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl.comm;

import org.aion.p2p.P2pConstant;

public final class PeerMetric {

    int metricFailedConn;
    private long metricFailedConnTs;
    private long metricBanConnTs;

    public boolean shouldNotConn() {
        return (metricFailedConn > P2pConstant.STOP_CONN_AFTER_FAILED_CONN
                && ((System.currentTimeMillis() - metricFailedConnTs) > P2pConstant.FAILED_CONN_RETRY_INTERVAL))
                || ((System.currentTimeMillis() - metricBanConnTs) < P2pConstant.BAN_CONN_RETRY_INTERVAL);
    }

    public void incFailedCount() {
        metricFailedConn++;
        metricFailedConnTs = System.currentTimeMillis();
    }

    public void decFailedCount() {
        if (metricFailedConn > 0)
            metricFailedConn--;
    }

    public void ban() {
        metricBanConnTs = System.currentTimeMillis();
    }

    public boolean notBan() {
        return ((System.currentTimeMillis() - metricBanConnTs) > P2pConstant.BAN_CONN_RETRY_INTERVAL);
    }
}
