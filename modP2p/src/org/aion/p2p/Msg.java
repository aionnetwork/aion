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

package org.aion.p2p;

import org.aion.base.type.IMsg;
import org.aion.base.type.IMsgHeader;

/**
 * @author chris
 */
public abstract class Msg implements IMsg {

    private final IMsgHeader header;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _act byte
     * @warning: at the msg construction phase, len of msg is unknown
     * therefore right before socket.write, we need to figure
     * out len before preparing the byte[]
     */
    public Msg(short _ver, byte _ctrl, byte _act){
        this.header = new Header(_ver, _ctrl, _act, 0);
    }

    /**
     * @return Header
     */
    @Override
    public IMsgHeader getHeader() {
        return this.header;
    }

    /**
     * Returns a zero-length byte array, this method must be override. It is here so that subclasses
     * cast as different super classes will have compatible encode methods if super is invoked in
     * the encode method.
     * @return
     */
    @Override
    public byte[] encode() {
        return new byte[0];
    }

}