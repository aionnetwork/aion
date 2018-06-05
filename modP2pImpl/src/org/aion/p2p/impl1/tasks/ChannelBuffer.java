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

import org.aion.p2p.Header;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * @author chris
 */
class ChannelBuffer {

    class RouteStatus {
        long timestamp;
        int count;
        RouteStatus(){
            this.timestamp = System.currentTimeMillis();
            count = 0;
        }
    }

    private boolean showLog;

    private Map<Integer, RouteStatus> routes = new HashMap<>();

    ChannelBuffer(boolean _showLog){
        this.showLog = _showLog;
    }

    /**
     * @param _route          int
     * @param _maxReqsPerSec  int requests within 1 s
     * @return                boolean flag if under route control
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
            if(shouldRoute) {
                prev.count++;
            }

            if(showLog) {
                if(!shouldRoute) {
                    System.out.println(
                        "<p2p route-cooldown=" + _route + " node=" + this.displayId + " count="
                            + prev.count + ">");
                }
                // too many msgs
                //else
                //    System.out.println("<p2p route-cooldown=" + _route + " node=" + this.displayId + " count=" + prev.count + ">");
            }
            return shouldRoute;
        } else
            return true;
    }


    RouteStatus getRouteCount(int _route){
        return routes.get(_route);
    }

    // buffer for buffer remaining after NIO select read.
    byte[] remainBuffer;

    int buffRemain = 0;

    int nodeIdHash = 0;

    String displayId = "";

    Header header = null;

    private byte[] bsHead = new byte[Header.LEN];

    byte[] body = null;

    Lock lock = new java.util.concurrent.locks.ReentrantLock();

    /**
     * Indicates whether this channel is closed.
     */
    AtomicBoolean isClosed = new AtomicBoolean(false);

    void readHead(ByteBuffer buf) {
        buf.get(bsHead);
        try {
            header = Header.decode(bsHead);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void readBody(ByteBuffer buf) {
        body = new byte[header.getLen()];
        buf.get(body);
    }

    void refreshHeader() {
        header = null;
    }

    void refreshBody() {
        body = null;
    }

    /**
     * @return boolean
     */
    boolean isHeaderCompleted() {
        return header != null;
    }

    /**
     * @return boolean
     */
    boolean isBodyCompleted() {
        return this.header != null && this.body != null && body.length == header.getLen();
    }

}
