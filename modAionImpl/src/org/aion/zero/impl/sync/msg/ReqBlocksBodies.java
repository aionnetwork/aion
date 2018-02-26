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
import java.util.ArrayList;
import java.util.List;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;
import org.aion.zero.impl.sync.ACT;

public final class ReqBlocksBodies implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.REQ_BLOCKS_BODIES;

    private final List<byte[]> blocksHashes;

    public ReqBlocksBodies(final List<byte[]> _blocksHashes) {
        blocksHashes = _blocksHashes;
    }

    public static ReqBlocksBodies decode(final byte[] _msgBytes) {
        if (_msgBytes == null)
            return null;
        else {
            /**
             * _msgBytes % 32 needs to be equal to 0 TODO: need test & catch
             */
            List<byte[]> blocksHashes = new ArrayList<byte[]>();
            ByteBuffer bb = ByteBuffer.wrap(_msgBytes);
            int count = _msgBytes.length / 32;
            while (count > 0) {
                byte[] blockHash = new byte[32];
                bb.get(blockHash);
                blocksHashes.add(blockHash);
                count--;
            }
            return new ReqBlocksBodies(blocksHashes);
        }
    }

    public List<byte[]> getBlocksHashes() {
        return this.blocksHashes;
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

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(this.blocksHashes.size() * 32);
        for (byte[] blockHash : this.blocksHashes) {
            bb.put(blockHash);
        }
        return bb.array();
    }
}
