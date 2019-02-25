package org.aion.evtmgr.impl.callback;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.slf4j.Logger;

/** @author jay */
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
