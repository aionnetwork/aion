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

package org.aion.p2p.impl.comm;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author chris
 *
 */
public final class Act {

    public static final byte DISCONNECT = 0;

    public static final byte REQ_HANDSHAKE = 1;

    public  static final byte RES_HANDSHAKE = 2;

    public static final byte PING = 3;

    public static final byte PONG = 4;

    public static final byte REQ_ACTIVE_NODES = 5;

    public static final byte RES_ACTIVE_NODES = 6;

    public static final byte UNKNOWN = Byte.MAX_VALUE;

    private static Set<Byte> active = new HashSet<>() {{
        add(REQ_HANDSHAKE);
        add(RES_HANDSHAKE);
        add(REQ_ACTIVE_NODES);
        add(RES_ACTIVE_NODES);
    }};

    /**
     * @param _act byte
     * @return byte
     * method provided to filter any decoded p2p action (byte)
     */
    public static byte filter(byte _act){
        return active.contains(_act) ? _act : UNKNOWN;
    }

}