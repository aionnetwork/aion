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

import org.aion.mcf.types.BlockIdentifier;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

import java.util.List;

//import org.apache.commons.collections4.map.LRUMap;

/**
 *
 * @author chris
 * handler for request block headers from network
 *
 */
public final class ReqBlocksHeadersHandler extends Handler {

    private final Logger log;

    private final int max;

    private final IAionBlockchain blockchain;

    private final IP2pMgr p2pMgr;

    //private final Map<Long, A0BlockHeader> cache = Collections.synchronizedMap(new LRUMap<>(1024));

    public ReqBlocksHeadersHandler(final Logger _log, final IAionBlockchain _blockchain, final IP2pMgr _p2pMgr, int _max) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_HEADERS);
        this.log = _log;
        this.blockchain = _blockchain;
        this.p2pMgr = _p2pMgr;
        this.max = _max;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        ReqBlocksHeaders reqHeaders = ReqBlocksHeaders.decode(_msgBytes);
        if (reqHeaders != null) {
            long fromBlock = reqHeaders.getFromBlock();
            int take = reqHeaders.getTake();
            if (log.isDebugEnabled()) {
                this.log.debug("<req-headers from-number={} size={} node={}>", fromBlock, take, _displayId);
            }
            List<A0BlockHeader> headers = this.blockchain.getListOfHeadersStartFrom(
                    new BlockIdentifier(null, fromBlock), 0, Math.min(take, max), false);
            ResBlocksHeaders rbhs = new ResBlocksHeaders(headers);
            this.p2pMgr.send(_nodeIdHashcode, rbhs);
        } else {
            //p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            this.log.error("<req-headers decode-error msg-bytes={} node={}>", _msgBytes == null ? 0 : _msgBytes.length,
                    _nodeIdHashcode);
        }
    }
}