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
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl.sync;

public class ACT {
    public static final byte REQ_STATUS = 0, RES_STATUS = 1, REQ_BLOCKS_HEADERS = 2, RES_BLOCKS_HEADERS = 3,
            REQ_BLOCKS_BODIES = 4, RES_BLOCKS_BODIES = 5, BROADCAST_TX = 6, BROADCAST_NEWBLOCK = 7, MAX = 8;

    private byte value;

    private static final ACT[] acts = new ACT[MAX];

    static {
        for (byte i = 0; i < MAX; i++) {
            acts[i] = new ACT(i);
        }
    }

    ACT(byte _value) {
        this.value = _value;
    }

    public int getValue() {
        return this.value;
    }

    public static ACT getType(final int _actInt) {
        if (_actInt >= MAX || _actInt < 0)
            return null;
        else
            return acts[_actInt];
    }

}
