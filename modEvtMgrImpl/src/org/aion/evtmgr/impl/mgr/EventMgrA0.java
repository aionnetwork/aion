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

import java.util.List;
import java.util.Properties;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractEventMgr;
import org.aion.evtmgr.impl.handler.BlockHandler;
import org.aion.evtmgr.impl.handler.ConsensusHandler;
import org.aion.evtmgr.impl.handler.MinerHandler;
import org.aion.evtmgr.impl.handler.TxHandler;

/**
 * @author jay
 *
 */
public class EventMgrA0 extends AbstractEventMgr implements IEventMgr {

    public EventMgrA0(Properties config) {
        super();

        if (config == null) {
            throw new NullPointerException();
        }

        IHandler txHdr = new TxHandler();
        this.handlers.put(txHdr, txHdr);

        IHandler consHdr = new ConsensusHandler();
        this.handlers.put(consHdr, consHdr);

        IHandler blkHdr = new BlockHandler();
        this.handlers.put(blkHdr, blkHdr);

        IHandler minerHdr = new MinerHandler();
        this.handlers.put(minerHdr, minerHdr);
        // setPoolArgs(config);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.evt.api.IEventMgr#registerEvent(java.util.List)
     */
    @Override
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

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.evt.api.IEventMgr#unregisterEvent(java.util.List)
     */
    @Override
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

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.evt.api.IEventMgr#newEvents(java.util.List)
     */
    @Override
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

    @Override
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
