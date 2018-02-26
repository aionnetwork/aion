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

import java.util.List;

import org.aion.p2p.ICallback;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.CTRL;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.slf4j.Logger;

public final class ReqBlocksBodiesCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.REQ_BLOCKS_BODIES;

    private final Logger log;

    private final IAionBlockchain blockchain;

    private final IP2pMgr p2pMgr;

    public ReqBlocksBodiesCallback(final Logger _log, final IAionBlockchain _blockchain, final IP2pMgr _p2pMgr) {
        this.log = _log;
        this.blockchain = _blockchain;
        this.p2pMgr = _p2pMgr;
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
        ReqBlocksBodies reqBlocks = ReqBlocksBodies.decode(_msgBytes);
        if (reqBlocks != null) {
            List<byte[]> blockBodies = this.blockchain.getListOfBodiesByHashes(reqBlocks.getBlocksHashes());
            this.p2pMgr.send(_nodeId, new ResBlocksBodies(blockBodies));
            this.log.debug("<req-bodies req-take={} res-take={} from-node={}>", reqBlocks.getBlocksHashes().size(),
                    blockBodies.size(), java.util.Arrays.hashCode(_nodeId));

        } else
            this.log.error("<req-bodies decode-msg>");
    }
}
