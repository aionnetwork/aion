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

package org.aion.p2p.a0.msg;

/**
 *
 * @author chris
 *
 */
public enum ACT {

    DISCONNECT(0),

    REQ_HANDSHAKE(1),
    RES_HANDSHAKE(2),

    @Deprecated
    PING(3),
    @Deprecated
    PONG(4),

    REQ_ACTIVE_NODES(5),
    RES_ACTIVE_NODES(6),

    UNKNOWN(127);

    public final static int MAX = 127;

    public final static int MIN = 0;

    private int value;

    private static final ACT[] intMapType = new ACT[MAX + 1];

    static {
        for (ACT type : ACT.values()) {
            intMapType[0xff & type.value] = type;
        }
    }

    ACT(final int _value) {
        this.value = _value;
    }

    public int getValue() {
        return this.value;
    }

    public static ACT getType(final int _actInt) {
        if(_actInt < MIN || _actInt > MAX)
            return UNKNOWN;
        else {
            ACT act = intMapType[0xff & _actInt];
            return act == null ? UNKNOWN : act;
        }
    }

}