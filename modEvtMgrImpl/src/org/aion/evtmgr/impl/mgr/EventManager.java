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

package org.aion.evtmgr.impl.mgr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * @author jay
 *
 */
public class EventManager implements IEventMgr {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    protected Map<String, IHandler> handlers;

    public EventManager(Map<String, IHandler> handlers) {
        this.handlers = new ConcurrentHashMap<>(handlers);
    }

    protected EventManager() {
        this.handlers = new ConcurrentHashMap<>();
    }

    public void start() {
        for (Map.Entry<String, IHandler> m : this.handlers.entrySet()) {
            if (m.getValue() != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("EventManager.start ", m.getValue().getClass().getSimpleName());
                }
                m.getValue().start();
            }
        }
    }

    public void shutDown() throws InterruptedException {
        for (Map.Entry<String, IHandler> m : this.handlers.entrySet()) {
            if (m.getValue() != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("EventManager.shutdown ", m.getValue().getClass().getSimpleName());
                }
                m.getValue().stop();
            }
        }
    }

    public List<IHandler> getHandlerList() {
        List<IHandler> hds = new ArrayList<>();
        for (Map.Entry<String, IHandler> m : this.handlers.entrySet()) {
            if (m.getValue() != null) {
                hds.add(m.getValue());
            }
        }

        return hds;
    }

    public IHandler getHandler(int _type) {
        for (Map.Entry<String, IHandler> m : this.handlers.entrySet()) {
            if (m.getValue() != null && m.getValue().getType() == _type) {
                return m.getValue();
            }
        }

        if (LOG.isErrorEnabled()) {
            LOG.error("Can't find handler type[{}] ", _type);
        }

        return null;
    }

    public boolean registerEvent(List<IEvent> _evt) {
        synchronized (this) {
            for (IEvent e : _evt) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("EVTMGR.registerEvent EventType [{}] CallbackType [{}]", e.getEventType(),
                            e.getCallbackType());
                }

                IHandler hdr = this.getHandler(e.getEventType());
                if (hdr == null) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("EVTMGR.registerEvent can't find the handler base on the EventType [{}]",
                                e.getEventType());
                    }
                    return false;
                }

                hdr.addEvent(e);
            }
        }
        return true;
    }

    public boolean unregisterEvent(List<IEvent> _evt) {
        synchronized (this) {
            for (IEvent e : _evt) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("EVTMGR.unregisterEvent EventType [{}]", e.getEventType());
                }

                IHandler hdr = this.getHandler(e.getEventType());
                if (hdr == null) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("EVTMGR.unregisterEvent can't find the handler base on the EventType [{}]",
                                e.getEventType());
                    }
                    return false;
                }

                hdr.removeEvent(e);
            }
        }
        return true;
    }

    public boolean newEvents(List<IEvent> _evt) {
        for (IEvent e : _evt) {
            IHandler hdr = this.getHandler(e.getEventType());
            if (hdr == null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("EVTMGR.newEvents can't find the handler[{}]", e.getEventType());
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("EVTMGR.newEvents eCBT:[{}] eEVT:[{}]", e.getCallbackType(), e.getEventType());
                }

                hdr.onEvent(e);
            }
        }

        return true;
    }

    public boolean newEvent(IEvent _evt) {
        IHandler hdr = this.getHandler(_evt.getEventType());
        if (hdr == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("EVTMGR.newEvent can't find the handler[{}]", _evt.getEventType());
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("EVTMGR.newEvent eCBT:[{}] eEVT:[{}]", _evt.getCallbackType(), _evt.getEventType());
            }

            hdr.onEvent(_evt);
        }

        return true;
    }
}
