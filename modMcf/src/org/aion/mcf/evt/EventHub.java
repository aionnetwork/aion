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

package org.aion.mcf.evt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event Hub.
 */
public class EventHub<D extends EvtData> {

    List<EvtCb<D>> subs[];

    public EventHub(int size) {
        subs = new List[size];
        for (int i = 0; i < size; i++) {
            subs[i] = new CopyOnWriteArrayList<>();
        }
    }

    public void reg(int type, EvtCb<D> t) {
        subs[type].add(t);
    }

    public void fire(int type, D data) {
        subs[type].forEach((t) -> {
            t.call(data);
        });
    }

}
