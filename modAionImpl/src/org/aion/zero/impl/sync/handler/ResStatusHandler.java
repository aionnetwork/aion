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
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 * <ether.camp> team through the ethereumJ library.
 * Ether.Camp Inc. (US) team through Ethereum Harmony.
 * John Tromp through the Equihash solver.
 * Samuel Neves through the BLAKE2 implementation.
 * Zcash project team.
 * Bitcoinj team.
 */

package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.*;
import org.slf4j.Logger;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.aion.zero.impl.sync.Act;

import java.math.BigInteger;

/**
 * @author chris
 */
public final class ResStatusHandler extends Handler {

    private final Logger log;

    private final IP2pMgr p2pMgr;

    private final SyncMgr syncMgr;

    public ResStatusHandler(final Logger _log, final IP2pMgr _p2pMgr, final SyncMgr _syncMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_STATUS);
        this.log = _log;
        this.p2pMgr = _p2pMgr;
        this.syncMgr = _syncMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0)
            return;
        ResStatus rs = ResStatus.decode(_msgBytes);

        if (rs == null) {
            //p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            this.log.error("<res-status decode-error from {} len: {}>", _displayId, _msgBytes.length);

            if (this.log.isTraceEnabled()) {
                this.log.trace("res-status decode-error dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }

        INode node = this.p2pMgr.getActiveNodes().get(_nodeIdHashcode);
        if (node != null) {
            if (log.isDebugEnabled()) {
                this.log.debug("<res-status best-block={} node={}>", rs.getBestBlockNumber(), _displayId);
            }
            this.p2pMgr.getNodeMgr().updateAllNodesInfo(node);
            long remoteBestBlockNumber = rs.getBestBlockNumber();
            byte[] remoteBestBlockHash = rs.getBestHash();
            byte[] remoteTdBytes = rs.getTotalDifficulty();
            if(remoteTdBytes != null && remoteBestBlockHash != null){
                BigInteger remoteTotalDifficulty = new BigInteger(1, remoteTdBytes);
                node.updateStatus(remoteBestBlockNumber, remoteBestBlockHash, remoteTotalDifficulty);
                syncMgr.updateNetworkStatus(_displayId, remoteBestBlockNumber, remoteBestBlockHash, remoteTotalDifficulty);
            }
        }
    }
}