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

package org.aion.evtmgr.impl.abs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * @author jay
 *
 */
public abstract class AbstractHandler {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());

    private Set<IEvent> events = new HashSet<>();
    private BlockingQueue<IEvent> queue = new LinkedBlockingQueue<>();
    private List<IEventCallback> eventCallback = new CopyOnWriteArrayList<>();
    private AtomicBoolean interrupt = new AtomicBoolean(false);
    private boolean interrupted = false;
    private int handlerType;

    protected Thread dispatcher = new Thread(() -> {
        try {
            while (!interrupt.get()) {
                IEvent e = queue.take();
                if (e.getEventType() != EventDummy.getTypeStatic() && events.contains(e)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("dispatcher e[{}]", e.getEventType());
                    }

                    try {
                        dispatch(e);
                    } catch (Throwable ex) {
                        LOG.error("Failed to dispatch event: eventType = {}, callbackType = {}, {}", e.getEventType(), e.getCallbackType(), ex.toString());
                    }
                }
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("dispatcher interrupted!");
            }

            queue.clear();
            interrupted = true;
        } catch (InterruptedException e) {
            LOG.error("Handler interrupt exception {}", e.toString());
        } catch (Error e) {
            LOG.error("Handler interrupt error {}", e.toString());
        }
    });

    public AbstractHandler(int value) {
        handlerType = value;
    }

    public synchronized boolean addEvent(IEvent _evt) {
        try {
            return this.events.add(_evt);
        } catch (Throwable e) {
            LOG.error("addEvent exception {}", e.toString());
            return false;
        }
    }

    public synchronized boolean removeEvent(IEvent _evt) {
        try {
            return this.events.remove(_evt);
        } catch (Throwable e) {
            LOG.error("removeEvent exception {}", e.toString());
            return false;
        }
    }

    public void stop() throws InterruptedException {

        interrupt.set(true);
        try {
            this.queue.add(new EventDummy());
        } catch (Throwable e) {
            LOG.error("stop exception {}", e.toString());
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Handler {} dispatcher interrupting..", this.getType());
        }

        int cnt = 0;
        while (!interrupted && (cnt++ < 10)) {
            System.out.print(".");
            Thread.sleep(1000);
        }

        if (cnt > 9) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Handler {} dispatcher can't be closed!", this.getType());
            }
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Handler {} dispatcher closed!", this.getType());
        }
    }


    private <E extends IEvent> void dispatch(E event) {
        if (this.typeEqual(event.getEventType())) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("CB size:[{}] cbType:[{}]", this.eventCallback.size(), event.getCallbackType());
            }

            for (IEventCallback cb : this.eventCallback) {
                cb.onEvent(event);
            }
        }
    }

    public synchronized void eventCallback(IEventCallback _evtCallback) {
        this.eventCallback.add(_evtCallback);
    }

    private boolean typeEqual(int _type) {
        return (this.getType() == _type);
    }

    public void start() {

        if (!this.dispatcher.isAlive()) {
            this.dispatcher.start();
        }
    }


    public void onEvent(IEvent _evt) {
        try {
            this.queue.add(_evt);
        } catch (Throwable e) {
            LOG.error("onEvent exception! {}", e.toString());
        }
    }

    public int getType() {
        return handlerType;
    }
}
