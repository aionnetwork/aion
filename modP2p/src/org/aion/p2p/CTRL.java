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

package org.aion.p2p;

/**
 * 
 * 
 * @author chris
 *
 */
public enum CTRL {

    /**
     * module specific controllers
     */
    NET0(0), SYNC0(1), UNKNOWN(127);

    final static int MAX = 127;

    final static int MIN = 0;

    private int value;

    private final static CTRL[] intMapType = new CTRL[MAX + 1];

    static {
        for (CTRL type : CTRL.values()) {
            intMapType[0xff & type.value] = type;
        }
    }

    CTRL(final int _value) {
        this.value = _value;
    }

    public int getValue() {
        return this.value;
    }

    public static CTRL getType(final int _ctrlInt) {
        if (_ctrlInt < MIN || _ctrlInt > MAX)
            return UNKNOWN;
        else {
            CTRL ctrl = intMapType[0xff & _ctrlInt];
            return ctrl == null ? UNKNOWN : ctrl;
        }
    }
}