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
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * @author jay
 * handler for new block broadcasted from network
 */
public final class BroadcastNewBlockHandler extends Handler {

    private final Logger log;

    private final BlockPropagationHandler propHandler;

    private final IP2pMgr p2pMgr;


    public BroadcastNewBlockHandler(final Logger _log, final BlockPropagationHandler propHandler, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_BLOCK);
        this.log = _log;
        this.propHandler = propHandler;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null)
            return;
        byte[] rawdata = BroadcastNewBlock.decode(_msgBytes);
        if (rawdata == null) {
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            log.error("<new-block-handler decode-error, from {} len: {}>",
                    _displayId,
                    _msgBytes.length);
            if (log.isTraceEnabled()) {
                log.trace("new-block-handler dump: {}", ByteUtil.toHexString(_msgBytes));
            }
            return;
        }

        AionBlock block = new AionBlock(rawdata);

        BlockPropagationHandler.PropStatus result = this.propHandler.processIncomingBlock(_nodeIdHashcode, _displayId, block);

        if (this.log.isDebugEnabled()) {
            String hash = block.getShortHash();
            hash = hash != null ? hash : "null";
            this.log.debug("<block-prop node=" + _displayId + " block-hash=" + hash + " status=" + result.name() + ">");
        }
    }
}
