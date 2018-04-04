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

package org.aion.p2p.impl.zero.msg;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.p2p.impl.comm.Act;

/**
 *
 * @author chris
 *
 */
public class ResHandshake extends Msg {

    private final boolean success;

    public final static int LEN = 1;

    public ResHandshake(boolean _success) {
        super(Ver.V0, Ctrl.NET, Act.RES_HANDSHAKE);
        this.success = _success;
    }

    public boolean getSuccess() {
        return this.success;
    }

    public static ResHandshake decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length < LEN)
            return null;
        else
            return new ResHandshake(_bytes[0] == 0x01);
    }

    @Override
    public byte[] encode() {
        return this.success ? new byte[] { 0x01 } : new byte[] { 0x00 };
    }

}
