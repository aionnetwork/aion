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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.aion.p2p.P2pConstant;
import org.junit.Before;
import org.junit.Test;

public class PeerMetricTest {

    private PeerMetric metric;

    @Before
    public void setup() {
        metric = new PeerMetric(P2pConstant.FAILED_CONN_RETRY_INTERVAL);
    }

    @Test
    public void testBan() throws InterruptedException {
        metric.ban();
        assertFalse(metric.notBan());

        Thread.sleep(P2pConstant.FAILED_CONN_RETRY_INTERVAL + 1);
        assertTrue(metric.notBan());
    }

    @Test
    public void testBanInterval() {
        metric = new PeerMetric();
        assertEquals(P2pConstant.BAN_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(Integer.MIN_VALUE);
        assertEquals(P2pConstant.BAN_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(Integer.MAX_VALUE);
        assertEquals(P2pConstant.BAN_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(2999);
        assertEquals(P2pConstant.BAN_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(P2pConstant.FAILED_CONN_RETRY_INTERVAL);
        assertEquals(P2pConstant.FAILED_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(3001);
        assertEquals(3001, metric.getBanInterval());

        metric = new PeerMetric(86400001);
        assertEquals(P2pConstant.BAN_CONN_RETRY_INTERVAL, metric.getBanInterval());

        metric = new PeerMetric(86400000);
        assertEquals(86400000, metric.getBanInterval());

        metric = new PeerMetric(86399999);
        assertEquals(86399999, metric.getBanInterval());
    }

    @Test
    public void testShouldNotConn() throws InterruptedException {
        int cnt = 0;
        while (++cnt < P2pConstant.STOP_CONN_AFTER_FAILED_CONN + 1) {
            metric.incFailedCount();
            assertFalse(metric.shouldNotConn());
        }
        metric.incFailedCount();
        assertTrue(metric.shouldNotConn());
        Thread.sleep(P2pConstant.FAILED_CONN_RETRY_INTERVAL + 1);
        assertFalse(metric.shouldNotConn());
    }

    @Test
    public void testShouldNotConn2() throws InterruptedException {
        int cnt = 0;
        while (++cnt < P2pConstant.STOP_CONN_AFTER_FAILED_CONN + 1) {
            metric.incFailedCount();
            assertFalse(metric.shouldNotConn());
        }
        metric.incFailedCount();
        assertTrue(metric.shouldNotConn());
        metric.decFailedCount();
        assertFalse(metric.shouldNotConn());

        metric.incFailedCount();
        assertTrue(metric.shouldNotConn());
        Thread.sleep(P2pConstant.FAILED_CONN_RETRY_INTERVAL + 1);
        assertFalse(metric.shouldNotConn());
    }

    @Test
    public void testShouldNotConn3() throws InterruptedException {
        assertFalse(metric.shouldNotConn());
        metric.ban();
        assertTrue(metric.shouldNotConn());
        Thread.sleep(P2pConstant.FAILED_CONN_RETRY_INTERVAL + 1);
        assertFalse(metric.shouldNotConn());
    }
}
