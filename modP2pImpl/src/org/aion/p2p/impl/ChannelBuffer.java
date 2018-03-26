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
package org.aion.p2p.impl;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author chris
 *
 */
class ChannelBuffer {

    int nodeIdHash = 0;

    ByteBuffer headerBuf = ByteBuffer.allocate(Header.LEN);

    ByteBuffer bodyBuf = null;

    Header header = null;

    byte[] body = null;

    /**
     * write flag
     */
    public AtomicBoolean onWrite = new AtomicBoolean(false);

    public BlockingQueue<Msg> messages = new ArrayBlockingQueue<>(128);

    void refreshHeader(){
        headerBuf.clear();
        header = null;
    }

    void refreshBody(){
        bodyBuf = null;
        body = null;
    }

    /**
     * @return boolean
     */
    boolean isHeaderCompleted(){
        return header != null;
    }

    /**
     * @return boolean
     */
    boolean isBodyCompleted() {
        return this.header != null && this.body != null && body.length == header.getLen();
    }

}
