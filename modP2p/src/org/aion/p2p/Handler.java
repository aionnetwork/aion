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

/**
 * @author chris
 */
public abstract class Handler {

    private Header header;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _act byte
     */
    public Handler(short _ver, byte _ctrl, byte _act){
        this.header = new Header(_ver, _ctrl, _act, 0);
    }

    /**
     * @return Header
     */
    public Header getHeader(){
        return this.header;
    }

    /**
     * @param _id int - node id hashcode
     * @param _displayId
     * @param _msg byte[]
     */
    public abstract void receive(int _id, String _displayId, final byte[] _msg);

    public void shutDown() {}
}