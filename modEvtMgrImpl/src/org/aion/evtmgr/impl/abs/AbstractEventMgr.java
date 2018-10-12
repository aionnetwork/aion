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

package org.aion.evtmgr.impl.abs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.evtmgr.IHandler;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * @author jay
 */
public abstract class AbstractEventMgr {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.EVTMGR.toString());
    protected Map<IHandler, IHandler> handlers;
    private AtomicBoolean run = new AtomicBoolean(false);

    protected AbstractEventMgr() {
        handlers = new ConcurrentHashMap<>();
    }

    public void start() {
        if (!run.get()) {
            for (Map.Entry<IHandler, IHandler> m : this.handlers.entrySet()) {
                if (m.getKey() != null) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("AbstractEventMgr.start ", m.getKey().getClass().getSimpleName());
                    }
                    m.getKey().start();
                }
            }
            run.set(true);
        }
    }

    public void shutDown() throws InterruptedException {

        if (!run.getAndSet(false)) {
            return;
        }

        for (Map.Entry<IHandler, IHandler> m : this.handlers.entrySet()) {
            if (m.getKey() != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("AbstractEventMgr.shutdown ", m.getKey().getClass().getSimpleName());
                }
                m.getKey().stop();
            }
        }
    }

    public List<IHandler> getHandlerList() {
        List<IHandler> hds = new ArrayList<>();
        for (Map.Entry<IHandler, IHandler> m : this.handlers.entrySet()) {
            if (m.getKey() != null) {
                hds.add(m.getKey());
            }
        }

        return hds;
    }

    public IHandler getHandler(int _type) {
        for (Map.Entry<IHandler, IHandler> m : this.handlers.entrySet()) {
            if (m.getKey() != null && m.getKey().getType() == _type) {
                return m.getKey();
            }
        }

        if (LOG.isErrorEnabled()) {
            LOG.error("Can't find handler type[{}] ", _type);
        }

        return null;
    }
}
