package org.aion.evtmgr.impl.es;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.evt.EventDummy;
import org.slf4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventExecuteService {

    private ArrayBlockingQueue<IEvent> callbackEvt;
    private ExecutorService es;
    private static Logger LOG;
    private String thName;

    public EventExecuteService(final int qSize, final String threadName, final int threadPriority, final Logger log) {
        if (threadName == null || log == null ) {
            throw new NullPointerException();
        }

        if (qSize < 100 || threadPriority < 1 || threadPriority > 10) {
            throw new IllegalArgumentException();
        }

        LOG = log;
        thName = threadName;

        callbackEvt = new ArrayBlockingQueue<>(qSize, true);

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
        try {
            return callbackEvt.add(event);
        } catch (IllegalStateException e) {
            LOG.error("ExecutorService Q is full!");
            return false;
        }
    }


    public void shutdown() {
        callbackEvt.clear();
        callbackEvt.add(new EventDummy());
        es.shutdown();
    }
}
