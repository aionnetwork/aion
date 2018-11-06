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

package org.aion.p2p.impl1.tasks;

import static org.aion.p2p.impl1.P2pMgr.p2pLOG;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.p2p.Header;

/** @author chris */
class ChannelBuffer {

    byte[] body = null;
    Lock lock = new ReentrantLock();
    private Header header = null;
    // buffer for buffer remaining after NIO select read.
    private byte[] remainBuffer;
    private int buffRemain = 0;
    private int nodeIdHash;
    private String displayId;
    private byte[] bsHead = new byte[Header.LEN];
    private AtomicBoolean closed = new AtomicBoolean(false);

    private Map<Integer, RouteStatus> routes = new HashMap<>();

    ChannelBuffer() {}

    public String getDisplayId() {
        return displayId;
    }

    void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    int getNodeIdHash() {
        return nodeIdHash;
    }

    void setNodeIdHash(int nodeIdHash) {
        this.nodeIdHash = nodeIdHash;
    }

    /** Indicates whether this channel is closed. */
    boolean isClosed() {
        return closed.get();
    }

    void setClosed() {
        this.closed.set(true);
    }

    int getBuffRemain() {
        return buffRemain;
    }

    void setBuffRemain(int buffRemain) {
        this.buffRemain = buffRemain;
    }

    /**
     * @param _route int
     * @param _maxReqsPerSec int requests within 1 s
     * @return boolean flag if under route control
     */
    synchronized boolean shouldRoute(int _route, int _maxReqsPerSec) {
        long now = System.currentTimeMillis();
        RouteStatus prev = routes.putIfAbsent(_route, new RouteStatus());
        if (prev != null) {
            if ((now - prev.timestamp) > 1000) {
                prev.count = 0;
                prev.timestamp = now;
                return true;
            }
            boolean shouldRoute = prev.count < _maxReqsPerSec;
            if (shouldRoute) {
                prev.count++;
            } else {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug(
                            "route-cooldown={} node={} count={}",
                            _route,
                            this.getDisplayId(),
                            prev.count);
                }
            }

            return shouldRoute;
        } else {
            return true;
        }
    }

    RouteStatus getRouteCount(int _route) {
        return routes.get(_route);
    }

    void readHead(ByteBuffer buf) {
        if (buf.array().length < bsHead.length) {
            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug("ChannelBuffer readHead short buffer size");
            }
            return;
        }

        buf.get(bsHead);
        try {
            header = Header.decode(bsHead);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug("ChannelBuffer readHead exception.", e);
            }
        }
    }

    void readBody(ByteBuffer buf) {
        if (isHeaderNotCompleted()) {
            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug("ChannelBuffer readBody no header.");
            }
            return;
        }

        if (buf.array().length < header.getLen()) {
            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug("ChannelBuffer readBody short buffer size.");
            }
            return;
        }

        body = new byte[header.getLen()];
        buf.get(body);
    }

    void refreshHeader() {
        header = null;
    }

    void refreshBody() {
        body = null;
    }

    /** @return boolean */
    boolean isHeaderNotCompleted() {
        return header == null;
    }

    /** @return boolean */
    boolean isBodyNotCompleted() {
        return header == null || body == null || body.length != header.getLen();
    }

    byte[] getRemainBuffer() {
        return remainBuffer;
    }

    void setRemainBuffer(byte[] remainBuffer) {
        this.remainBuffer = remainBuffer;
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header _header) {
        header = _header;
    }

    class RouteStatus {

        long timestamp;
        int count;

        RouteStatus() {
            this.timestamp = System.currentTimeMillis();
            count = 0;
        }
    }
}
