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

import org.aion.base.type.ITransaction;
import org.aion.p2p.CTRL;
import org.aion.p2p.IMsg;
import org.aion.p2p.P2pVer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.ACT;

/**
 * @author jay
 *
 */
public final class BroadcastTx implements IMsg {

    private final static byte ctrl = CTRL.SYNC0;
    private final static byte act = ACT.BROADCAST_TX;

    private final List<ITransaction> txl;

    public BroadcastTx(final List<ITransaction> _txl) {
        this.txl = _txl;
    }

    public short getVer() {
        return P2pVer.VER0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.net.nio.IMsg#getCtrl()
     */
    @Override
    public byte getCtrl() {
        return ctrl;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.net.nio.IMsg#getAct()
     */
    @Override
    public byte getAct() {
        return act;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.net.nio.IMsg#encode()
     */
    @Override
    public byte[] encode() {
        List<byte[]> encodedTx = new ArrayList<>();

        for (ITransaction tx : txl) {
            encodedTx.add(tx.getEncoded());
        }

        return RLP.encodeList(encodedTx.toArray(new byte[encodedTx.size()][]));
    }

    /*
     * return the encodedData of the Transaction list, the caller function need
     * to cast the return byte[] array
     */
    public static List<byte[]> decode(final byte[] _msgBytes) {
        RLPList paramsList = (RLPList) RLP.decode2(_msgBytes).get(0);
        List<byte[]> txl = new ArrayList<>();
        for (int i = 0; i < paramsList.size(); ++i) {
            RLPList rlpData = ((RLPList) paramsList.get(i));
            txl.add(rlpData.getRLPData());
        }
        return txl;
    }
}
