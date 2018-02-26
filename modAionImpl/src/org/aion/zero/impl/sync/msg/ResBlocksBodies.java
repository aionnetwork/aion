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

import java.util.ArrayList;
import java.util.List;

import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.ACT;

/**
 * 
 * @author chris TODO: follow same construction, decode & encode rule as
 *         ResBlocksHeaders in future. Need to update INcBlockchain
 */

public final class ResBlocksBodies implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_BLOCKS_BODIES;

    // private final List<NcBlock> blocks;
    //
    // public ResBlocks(final List<NcBlock> _blocks) {
    // blocks = _blocks;
    // }

    private final List<byte[]> blocksBodies;

    public ResBlocksBodies(final List<byte[]> _blocksBodies) {
        blocksBodies = _blocksBodies;
    }

    public static ResBlocksBodies decode(final byte[] _msgBytes) {
        RLPList paramsList = (RLPList) RLP.decode2(_msgBytes).get(0);
        List<byte[]> blocksBodies = new ArrayList<>();
        for (int i = 0; i < paramsList.size(); ++i) {
            RLPList rlpData = ((RLPList) paramsList.get(i));
            blocksBodies.add(rlpData.getRLPData());
        }
        return new ResBlocksBodies(blocksBodies);
    }

    public List<byte[]> getBlocksBodies() {
        return this.blocksBodies;
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
        return RLP.encodeList(this.blocksBodies.toArray(new byte[this.blocksBodies.size()][]));
    }
}
