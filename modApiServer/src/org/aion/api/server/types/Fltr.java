/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Fltr {

    public final static short EVTS_MAX = 1000;

    public AtomicLong lastPollTime;

    public enum Type {
        EVENT, BLOCK, TRANSACTION, LOG
    }

    /**
     * because to use this member variable which cause illegalAccessError make
     * sure use synchronized method
     */
    private Type type;

    protected ArrayBlockingQueue<Evt> events = new ArrayBlockingQueue<>(EVTS_MAX);

    public Fltr(final Type _type) {
        this.lastPollTime = new AtomicLong(System.currentTimeMillis());
        this.type = _type;
    }

    public int getSize() {
        return this.events.size();
    }

    public synchronized Type getType() {
        return this.type;
    }

    public synchronized Object[] poll() {
        Object[] ret = events.toArray();
        this.lastPollTime.set(System.currentTimeMillis());
        this.events.clear();
        return ret;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - this.lastPollTime.get()) > 300000l;
    }

    public synchronized void add(Evt evt) {
        if (events.size() < EVTS_MAX)
            events.add(evt);
    }

}