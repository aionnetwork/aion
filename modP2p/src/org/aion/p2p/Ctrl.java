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

import java.util.HashSet;
import java.util.Set;

/**
 * @author chris
 */
public class Ctrl {

    public static final byte NET = 0;

    public static final byte SYNC = 1;

    public static final byte UNKNOWN = Byte.MAX_VALUE;

    private static Set<Byte> active = new HashSet<>() {{
        add(NET);
        add(SYNC);
    }};

    /**
     * @param _ctrl byte
     * @return byte method provided to filter any decoded ctrl (byte)
     */
    public static byte filter(byte _ctrl) {
        return active.contains(_ctrl) ? _ctrl : UNKNOWN;
    }

}