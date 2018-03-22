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

package org.aion.evtmgr.impl.callback;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.slf4j.Logger;

/**
 * @author jay
 *
 */
@SuppressWarnings("hiding")
public class EventCallback implements IEventCallback {
    EventExecuteService ees;
    static Logger LOG;
    public EventCallback(EventExecuteService _ees, Logger log) {
        if (_ees == null || log == null) {
            throw new NullPointerException();
        }

        ees = _ees;
        LOG = log;
    }

    public void onEvent(IEvent evt) {
        if (evt == null) {
            throw new NullPointerException();
        }

        try {
            ees.add(evt);
        } catch (Exception e) {
            LOG.error("{}", e.toString());
        }
    }
}
