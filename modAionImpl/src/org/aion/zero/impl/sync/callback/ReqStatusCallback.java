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

import org.aion.p2p.CTRL;
import org.aion.p2p.ICallback;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.ACT;
import org.slf4j.Logger;

public final class ReqStatusCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.REQ_STATUS;

    private final Logger log;

    private IAionBlockchain chain;

    private IP2pMgr mgr;

    private byte[] genesisHash;

    public ReqStatusCallback(final Logger _log, final IAionBlockchain _chain, final IP2pMgr _mgr,
            final byte[] _genesisHash) {
        this.log = _log;
        this.chain = _chain;
        this.mgr = _mgr;
        this.genesisHash = _genesisHash;
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
    public void receive(byte[] _nodeId, byte[] _msg) {
        this.log.debug("<req-status from-node={}>", java.util.Arrays.hashCode(_nodeId));
        this.mgr.send(_nodeId, new org.aion.zero.impl.sync.msg.ResStatus(this.chain.getBestBlock().getNumber(),
                this.chain.getTotalDifficulty().toByteArray(), this.chain.getBestBlockHash(), this.genesisHash));
    }
}
