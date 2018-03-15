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

package org.aion.p2p.impl;

final class PeerMetric {

    private static final int STOP_CONN_AFTER_FAILED_CONN = 2;
    private static final long FAILED_CONN_RETRY_INTERVAL = 3000;

    int metricFailedConn;
    private long metricFailedConnTs;

    boolean shouldNotConn() {
        return metricFailedConn > STOP_CONN_AFTER_FAILED_CONN && ((System.currentTimeMillis() - metricFailedConnTs ) > FAILED_CONN_RETRY_INTERVAL) ;
    }

    void incFailedCount() {
        metricFailedConn ++;
        metricFailedConnTs = System.currentTimeMillis();
    }

    void decFailedCount() {
        if (metricFailedConn > 0)
            metricFailedConn --;
    }

}
