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

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.impl.types.AionBlock;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author chris
 * handler for request block bodies broadcasted from network
 */
public final class ReqBlocksBodiesHandler extends Handler {

    private final Logger log;

    private final int max;

    private final IAionBlockchain blockchain;

    private final IP2pMgr p2pMgr;

    private final Map<ByteArrayWrapper, byte[]> cache = Collections.synchronizedMap(new LRUMap<>(1024));

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

            // limit number of blocks
            List<byte[]> hashes = reqBlocks.getBlocksHashes();
            hashes = hashes.size() > max ? hashes.subList(0, max) : hashes;

            // results
            List<byte[]> blockBodies = new ArrayList<>();

            // read from cache, then block store
            for (byte[] hash : hashes) {
                byte[] blockBytes = cache.get(ByteArrayWrapper.wrap(hash));
                if (blockBytes != null) {
                    blockBodies.add(blockBytes);
                } else {
                    AionBlock block = blockchain.getBlockByHash(hash);
                    if (block != null) {
                        blockBodies.add(block.getEncodedBody());
                        cache.put(ByteArrayWrapper.wrap(hash), block.getEncodedBody());
                    } else {
                        // not found
                        break;
                    }
                }
            }

            this.p2pMgr.send(_nodeIdHashcode, new ResBlocksBodies(blockBodies));
            if (log.isDebugEnabled()) {
                this.log.debug("<req-bodies req-size={} res-size={} node={}>", reqBlocks.getBlocksHashes().size(),
                        blockBodies.size(), _displayId);
            }

        } else {
            //p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            this.log.error("<req-bodies decode-error, unable to decode bodies from {}, len: {}>",
                    _displayId,
                    _msgBytes.length);

            if (this.log.isTraceEnabled()) {
                this.log.trace("req-bodies dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }
    }
}