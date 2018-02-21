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

import java.nio.ByteBuffer;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.Version;
import org.aion.p2p.a0.ACT;

/**
 * 
 * @author chris
 *
 */
public final class Disconnect implements IMsg {

    private final static byte ctrl = CTRL.NET0;
    
    private final static byte act = ACT.DISCONNECT;
    
    public final static int LEN = 40;
    
    private final byte[] reason;
    
    public short getVer() {
        return Version.ZERO;
    }
    
    public Disconnect(final byte[] _reason) {
        ByteBuffer bb;
        if(_reason == null)
            bb = ByteBuffer.wrap("unknown".getBytes());
        else 
            bb = ByteBuffer.wrap(_reason);
       
        reason = new byte[LEN];
        int len = Math.min(bb.capacity(), LEN);
        for(int i = 0; i < len; i++) {
            reason[i] = bb.get();
        }
    }
    
    public static Disconnect decode(final byte[] _bytes) {
        if(_bytes == null || _bytes.length != LEN) 
            return new Disconnect(null);
        else 
            return new Disconnect(_bytes);
    }
    
    public byte[] getReason() {
        return this.reason;
    }
    
    @Override
    public byte[] encode() {
        return reason;
    }

    @Override
    public byte getCtrl() {
        return ctrl;
    }

    @Override
    public byte getAct() {
        return act;
    }
    
}