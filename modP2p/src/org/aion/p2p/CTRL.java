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
public class CTRL {

    /**
     * module specific controllers
     */
    public static final byte NET0 = 0;
    public static final byte SYNC0 = 1;
    public static final byte UNKNOWN = 2;
    public static final byte MAX0 = 3;

    private byte value;

    static CTRL ctrls[] = new CTRL[] { new CTRL(NET0), new CTRL(SYNC0), new CTRL(UNKNOWN) };

    CTRL(byte _value) {
        this.value = _value;
    }

    public byte getValue() {
        return value;
    }

    public static CTRL getType(byte _ctrlInt) {
        if (_ctrlInt >= MAX0 || _ctrlInt < 0) {
            return ctrls[UNKNOWN];
        }
        return ctrls[_ctrlInt];
    }
}