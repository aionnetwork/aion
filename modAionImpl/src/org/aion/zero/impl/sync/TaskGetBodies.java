package org.aion.zero.impl.sync;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.types.A0BlockHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class TaskGetBodies implements Runnable {

    // timeout sent headers
    private final static int SENT_HEADERS_TIMEOUT = 5000;

    private final IP2pMgr p2p;

    private final AtomicBoolean run;

    private final BlockingQueue<HeadersWrapper> headersImported;

    private final ConcurrentHashMap<Integer, HeadersWrapper> headersSent;

    TaskGetBodies(
            final IP2pMgr _p2p,
            final AtomicBoolean _run,
            final BlockingQueue<HeadersWrapper> _headersImported,
            final ConcurrentHashMap<Integer, HeadersWrapper> _headersSent){
        this.p2p = _p2p;
        this.run = _run;
        this.headersImported = _headersImported;
        this.headersSent = _headersSent;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("sync-gb");
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        while (run.get()) {
            HeadersWrapper hw;
            try {
                hw = headersImported.take();
            } catch (InterruptedException e) {
                return;
            }

            int idHash = hw.getNodeIdHash();
            List<A0BlockHeader> headers = hw.getHeaders();
            synchronized (headersSent) {
                HeadersWrapper hw1 = headersSent.get(idHash);
                // already sent, check timeout and add it back if not timeout
                // yet
                if (hw1 != null) {
                    // not expired yet
                    if ((System.currentTimeMillis() - hw1.getTimestamp()) < SENT_HEADERS_TIMEOUT)
                        headersSent.put(idHash, hw1);
                } else {
                    this.headersSent.put(idHash, hw);
                    List<byte[]> headerHashes = new ArrayList<>();
                    for (A0BlockHeader h : headers) {
                        headerHashes.add(h.getHash());
                    }
                    this.p2p.send(idHash, new ReqBlocksBodies(headerHashes));
                }
            }
        }
    }
}
