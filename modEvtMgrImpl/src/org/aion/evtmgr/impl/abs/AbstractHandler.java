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
import java.util.concurrent.*;
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

    protected Set<IEvent> events = new HashSet<>();
    protected BlockingQueue<IEvent> queue = new LinkedBlockingQueue<IEvent>();
    protected List<IEventCallback> eventCallback = new CopyOnWriteArrayList<>();
    private AtomicBoolean interrupt = new AtomicBoolean(false);
    private boolean interruptted = false;

    protected ExecutorService es;

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
                    } catch (Exception ex) {
                        LOG.error("Failed to dispatch event: eventType = {}, callbackType = {}", e.getEventType(), e.getCallbackType(), ex);
                    }
                }
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("dispatcher interrupted!");
            }

            queue.clear();
            interruptted = true;
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    });

    protected AbstractHandler() {
        this.es = Executors.newWorkStealingPool(3);
    }

    public synchronized boolean addEvent(IEvent _evt) {
        return this.events.add(_evt);
    }

    public synchronized boolean removeEvent(IEvent _evt) {
        return this.events.add(_evt);
    }

    protected abstract <E extends IEvent> void dispatch(E _e);

    public void stop() throws InterruptedException {
        // In case the thread got stuck into the blocking queue.
        es.shutdown();

        interrupt.set(true);
        this.queue.add(new EventDummy());

        if (LOG.isInfoEnabled()) {
            LOG.info("Handler {} dispatcher interrupting..", this.getType());
        }

        int cnt = 0;
        while (!interruptted && (cnt++ < 10)) {
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

    public synchronized void eventCallback(IEventCallback _evtCallback) {
        this.eventCallback.add(_evtCallback);
    }

    public abstract int getType();

    public boolean typeEqual(int _type) {
        return (this.getType() == _type);
    }

    public void start() {

        if (!this.dispatcher.isAlive()) {
            this.dispatcher.start();
        }
    }
}
