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

package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;
import org.aion.zero.impl.sync.ACT;

public final class ReqBlocksHeaders implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.REQ_BLOCKS_HEADERS;

    /**
     * fromBlock(long), take(int)
     */
    private final static int len = 8 + 4;

    private final long fromBlock;

    private final int take;

    public ReqBlocksHeaders(final long _fromBlock, final int _take) {
        this.fromBlock = _fromBlock;
        this.take = _take;
    }

    public long getFromBlock() {
        return this.fromBlock;
    }

    public int getTake() {
        return this.take;
    }

    public static ReqBlocksHeaders decode(final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length != len)
            return null;
        else {
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            long _fromBlock = bb.getLong();
            int _take = bb.getInt();
            return new ReqBlocksHeaders(_fromBlock, _take);
        }
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.putLong(this.fromBlock);
        bb.putInt(this.take);
        return bb.array();
    }

    public short getVer() {
        return P2pVer.VER0;
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
