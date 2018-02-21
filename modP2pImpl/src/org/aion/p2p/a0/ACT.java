/*******************************************************************************
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
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.p2p.a0;

/**
 *
 * @author chris
 *
 */
public class ACT {

    public static final byte DISCONNECT = 0;
    public static final byte REQ_HANDSHAKE = 1;
    public static final byte RES_HANDSHAKE = 2;
    public static final byte PING = 3;
    public static final byte PONG = 4;
    public static final byte REQ_ACTIVE_NODES = 5;
    public static final byte RES_ACTIVE_NODES = 6;
    static final byte UNKNOWN = 7;

    static final byte MIN = 0;
    static final byte MAX = 8;

    private byte value;

    private static final ACT[] acts = new ACT[MAX];

    static {
        for (byte i = 0; i < MAX; i++) {
            acts[i] = new ACT(i);
        }
    }

    private ACT(byte _value) {
        this.value = _value;
    }

    public int getValue() {
        return this.value;
    }

    static ACT getType(int _actInt) {
        if (_actInt >= MAX || _actInt < 0)
            return acts[UNKNOWN];
        return acts[_actInt];
    }

}