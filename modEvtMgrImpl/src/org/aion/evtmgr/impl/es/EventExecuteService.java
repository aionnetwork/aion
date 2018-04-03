package org.aion.evtmgr.impl.es;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventExecuteService {

    private LinkedBlockingQueue<IEvent> callbackEvt;
    private ExecutorService es;
    private static Logger LOG;
    private String thName;
    private Set<Integer> filter;

    public EventExecuteService(final int qSize, final String threadName, final int threadPriority, final Logger log ) {
        if (threadName == null || log == null ) {
            throw new NullPointerException();
        }

        if (qSize < 100 || threadPriority < 1 || threadPriority > 10) {
            throw new IllegalArgumentException();
        }

        LOG = log;
        thName = threadName;

        filter = new HashSet<>();
        filter.add(0);

        callbackEvt = new LinkedBlockingQueue(qSize);

        es = Executors.newFixedThreadPool(1, arg0 -> {
            Thread thread = new Thread(arg0, threadName);
            thread.setPriority(threadPriority);
            return thread;
        });
    }

    public void start(Runnable r) {
        if (r == null) {
            throw new NullPointerException();
        }

        es.execute(r);
    }

    public IEvent take() {

        if (LOG.isTraceEnabled()) {
            LOG.trace("EventExecuteService {} q#[{}]", thName, callbackEvt.size());
        }

        try {
            return callbackEvt.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean add(IEvent event) {
        if (event == null) {
            throw new NullPointerException();
        }

        int sn = (event.getEventType() << 8) + event.getCallbackType();

        if (filter.contains(sn)) {

            try {
                return callbackEvt.add(event);
            } catch (IllegalStateException e) {
                LOG.error("ExecutorService Q is full!");
                return false;
            }
        } else {
            return false;
        }
    }


    public void shutdown() {
        callbackEvt.clear();
        callbackEvt.add(new EventDummy());
        es.shutdown();
    }

    public void setFilter(Set<Integer> filter) {
        this.filter = filter;
        this.filter.add(0);//Poison Pill
    }
}
