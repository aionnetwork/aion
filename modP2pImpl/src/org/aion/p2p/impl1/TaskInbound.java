package org.aion.p2p.impl1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.P2pConstant;
import org.slf4j.Logger;

public class TaskInbound implements Runnable {

    private final Logger p2pLOG, surveyLog;
    private final P2pMgr mgr;
    private final Selector selector;
    private final AtomicBoolean start;

    // used when survey logging
    private static final long MIN_DURATION = 60_000_000_000L; // 60 seconds
    private long waitTime = 0, processTime = 0;

    public TaskInbound(
            final Logger p2pLOG,
            final Logger surveyLog,
            final P2pMgr _mgr,
            final Selector _selector,
            final AtomicBoolean _start) {

        this.p2pLOG = p2pLOG;
        this.surveyLog = surveyLog;
        this.mgr = _mgr;
        this.selector = _selector;
        this.start = _start;
    }

    @Override
    public void run() {
        // for runtime survey information
        long startTime, duration;

        // readBuffer buffer pre-alloc. @ max_body_size
        ByteBuffer readBuf = ByteBuffer.allocate(P2pConstant.MAX_BODY_SIZE);

        while (start.get()) {

            startTime = System.nanoTime();
            try {
                // timeout set to 0.1 second
                if (this.selector.select(100) == 0) {
                    duration = System.nanoTime() - startTime;
                    waitTime += duration;
                    continue;
                }
            } catch (IOException | ClosedSelectorException e) {
                p2pLOG.debug("inbound-select-exception.", e);
                continue;
            }
            duration = System.nanoTime() - startTime;
            waitTime += duration;
            if (waitTime > MIN_DURATION) { // print and reset total time so far
                surveyLog.debug("TaskInbound: find selectors, duration = {} ns.", waitTime);
                waitTime = 0;
            }

            startTime = System.nanoTime();
            try {
                Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    ChannelBuffer cb = null;
                    SelectionKey key = null;
                    try {
                        key = keys.next();
                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            mgr.acceptConnection((ServerSocketChannel) key.channel());
                        }

                        if (key.isReadable()) {
                            cb = (ChannelBuffer) key.attachment();
                            if (cb == null) {
                                p2pLOG.error("inbound exception: attachment is null");
                                continue;
                            }
                            mgr.readBuffer(key, cb, readBuf);
                        }
                    } catch (Exception e) {
                        this.mgr.closeSocket(
                                key != null ? (SocketChannel) key.channel() : null,
                                (cb != null ? cb.getDisplayId() : null) + "-read-msg-exception ",
                                e);
                        if (cb != null) {
                            cb.setClosed();
                        }
                    } finally {
                        keys.remove();
                    }
                }
            } catch (ClosedSelectorException ex) {
                p2pLOG.error("inbound ClosedSelectorException.", ex);
            }
            duration = System.nanoTime() - startTime;
            processTime += duration;
            if (processTime > MIN_DURATION) { // print and reset total time so far
                surveyLog.debug("TaskInbound: process incoming msg, duration = {} ns.", processTime);
                processTime = 0;
            }
        }

        // print remaining total times
        surveyLog.debug("TaskInbound: find selectors, duration = {} ns.", waitTime);
        surveyLog.debug("TaskInbound: process incoming msg, duration = {} ns.", processTime);

        p2pLOG.info("p2p-pi shutdown");
    }
}
