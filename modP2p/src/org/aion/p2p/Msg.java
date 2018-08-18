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

package org.aion.p2p;

/** @author chris */
public abstract class Msg {

    private final Header header;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _act byte
     * @warning: at the msg construction phase, len of msg is unknown therefore right before
     *     socket.write, we need to figure out len before preparing the byte[]
     */
    public Msg(short _ver, byte _ctrl, byte _act) {
        this.header = new Header(_ver, _ctrl, _act, 0);
    }

    /** @return Header */
    public Header getHeader() {
        return this.header;
    }

    /**
     * Returns byte array encoding of message.
     *
     * @return
     */
    public abstract byte[] encode();
}
