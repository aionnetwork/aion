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
package org.aion.p2p;

/**
 * An interface for tracking peer connection and banning metrics.
 */
public interface IPeerMetric {

    /**
     * Returns true only if we should not accept any more connections.
     */
    boolean shouldNotConn();

    /**
     * Increments the failed connection counter.
     */
    void incFailedCount();

    /**
     * Decrements the failed connection counter.
     */
    void decFailedCount();

    /**
     * Sets the current time for tracking a banned connection.
     */
    void ban();

    /**
     * Returns true only if the time between now and the last ban is greater than the banned
     * connection retry interval.
     */
    boolean notBan();

}
