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

/** @author jay */
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
