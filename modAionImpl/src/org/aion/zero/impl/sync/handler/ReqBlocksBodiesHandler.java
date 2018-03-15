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

import java.util.List;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.slf4j.Logger;

/**
 * @author chris
 * handler for request block bodies broadcasted from network
 */
public final class ReqBlocksBodiesHandler extends Handler {

    private final Logger log;

    private final int max;

    private final IAionBlockchain blockchain;

    private final IP2pMgr p2pMgr;

    public ReqBlocksBodiesHandler(final Logger _log, final IAionBlockchain _blockchain, final IP2pMgr _p2pMgr, int _max) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_BODIES);
        this.log = _log;
        this.blockchain = _blockchain;
        this.p2pMgr = _p2pMgr;
        this.max = _max;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        ReqBlocksBodies reqBlocks = ReqBlocksBodies.decode(_msgBytes);
        if (reqBlocks != null) {
            List<byte[]> blockBodies = this.blockchain.getListOfBodiesByHashes(reqBlocks.getBlocksHashes());
            if(blockBodies.size() > max)
                blockBodies = blockBodies.subList(0, Math.min(max - 1, blockBodies.size() -1));
            this.p2pMgr.send(_nodeIdHashcode, new ResBlocksBodies(blockBodies));
            this.log.debug("<req-bodies req-take={} res-take={} from-node={}>", reqBlocks.getBlocksHashes().size(),
                    blockBodies.size(), _displayId);

        } else
            this.log.error("<req-bodies decode-msg>");
    }
}
