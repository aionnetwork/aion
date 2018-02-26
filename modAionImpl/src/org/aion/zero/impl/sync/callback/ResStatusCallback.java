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

package org.aion.zero.impl.sync.callback;

import java.util.Arrays;

import org.aion.p2p.CTRL;
import org.aion.p2p.ICallback;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pVer;
import org.aion.p2p.INode;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.slf4j.Logger;

public final class ResStatusCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_STATUS;

    private final Logger log;

    private final IP2pMgr p2pMgr;

    private final SyncMgr syncMgr;

    public ResStatusCallback(final Logger _log, final IP2pMgr _p2pMgr, final SyncMgr _syncMgr) {
        this.log = _log;
        this.p2pMgr = _p2pMgr;
        this.syncMgr = _syncMgr;
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
    public void receive(final byte[] _nodeId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0)
            return;
        ResStatus rs = ResStatus.decode(_msgBytes);
        INode node = this.p2pMgr.getActiveNodes().get(Arrays.hashCode(_nodeId));
        if (node != null) {
            this.log.debug("<res-status best-block={} from-node={}>", rs.getBestBlockNumber(),
                    java.util.Arrays.hashCode(_nodeId));
            long nodeBestBlockNumber = rs.getBestBlockNumber();
            node.setBestBlockNumber(nodeBestBlockNumber);
            syncMgr.updateNetworkBestBlock(nodeBestBlockNumber, rs.getBestHash());
        }
    }

}
