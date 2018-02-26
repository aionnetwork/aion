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
import org.aion.zero.types.A0BlockHeader;

/**
 * 
 * @author chris
 *
 */

public final class ResBlocksHeaders implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_BLOCKS_HEADERS;

    private final List<A0BlockHeader> blockHeaders;

    public ResBlocksHeaders(final List<A0BlockHeader> _blockHeaders) {
        blockHeaders = _blockHeaders;
    }

    public static ResBlocksHeaders decode(final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0)
            return null;
        else {
            try {
                RLPList list = (RLPList) RLP.decode2(_msgBytes).get(0);
                List<A0BlockHeader> blockHeaders = new ArrayList<>();
                for (int i = 0, m = list.size(); i < m; ++i) {
                    RLPList rlpData = ((RLPList) list.get(i));
                    blockHeaders.add(A0BlockHeader.fromRLP(rlpData, true));
                }
                return new ResBlocksHeaders(blockHeaders);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public List<A0BlockHeader> getHeaders() {
        return this.blockHeaders;
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
        List<byte[]> tempList = new ArrayList<>();
        for (A0BlockHeader blockHeader : this.blockHeaders) {
            tempList.add(blockHeader.getEncoded());
        }
        byte[][] bytesArray = tempList.toArray(new byte[tempList.size()][]);
        return RLP.encodeList(bytesArray);
    }

}
