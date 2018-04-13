/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */
package org.aion.p2p.impl1;

import org.aion.p2p.Header;
import org.aion.p2p.P2pConstant;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 *
 * @author chris
 *
 */
class ChannelBuffer {

    // buffer for buffer remaining after NIO select read.
    byte[] remainBuffer;
    
    int buffRemain = 0;

    int nodeIdHash = 0;

    Header header = null;

    byte[] bsHead = new byte[Header.LEN];

    byte[] body = null;

    Lock lock = new java.util.concurrent.locks.ReentrantLock();

    /**
     * write flag
     */
    public AtomicBoolean onWrite = new AtomicBoolean(false);

    /**
     * Indicates whether this channel is closed.
     */
    public AtomicBoolean isClosed = new AtomicBoolean(false);

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
