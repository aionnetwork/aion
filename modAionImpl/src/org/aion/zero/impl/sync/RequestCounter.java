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

package org.aion.zero.impl.sync;

/**
 * Used for tracking different types of requests made to peers.
 *
 * @author Alexandra Roatis
 */
public class RequestCounter {

    private long status = 0;
    private long headers = 0;
    private long bodies = 0;
    private long total = 0;

    public RequestCounter() {}

    public long getStatus() {
        return status;
    }

    public long getHeaders() {
        return headers;
    }

    public long getBodies() {
        return bodies;
    }

    public long getTotal() {
        return total;
    }

    public void incStatus() {
        this.status++;
        this.total++;
    }

    public void incHeaders() {
        this.headers++;
        this.total++;
    }

    public void incBodies() {
        this.bodies++;
        this.total++;
    }
}
