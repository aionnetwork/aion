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

package org.aion.zero.impl.sync.callback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.aion.p2p.Handler;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 *
 * @author chris
 *
 */
public final class ResBlocksBodiesCallback extends Handler {

    private final Logger log;

    private final SyncMgr syncMgr;

    public ResBlocksBodiesCallback(final Logger _log, final SyncMgr _syncMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        this.log = _log;
        this.syncMgr = _syncMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        ResBlocksBodies resBlocksBodies = ResBlocksBodies.decode(_msgBytes);
        List<A0BlockHeader> headers = this.syncMgr.getSentHeaders(_nodeIdHashcode);
        List<byte[]> bodies = resBlocksBodies.getBlocksBodies();
        if (headers != null && bodies != null && headers.size() == bodies.size()) {
            List<AionBlock> blocks = new ArrayList<>(bodies.size());
            Iterator<A0BlockHeader> headerIt = headers.iterator();
            Iterator<byte[]> bodyIt = bodies.iterator();
            boolean pass = true;
            while (headerIt.hasNext() && bodyIt.hasNext()) {
                AionBlock block = AionBlock.createBlockFromNetwork(headerIt.next(), bodyIt.next());
                if (block == null) {
                    pass = false;
                    break;
                } else
                    blocks.add(block);
            }
            this.syncMgr.clearSentHeaders(_nodeIdHashcode);
            if (pass) {
                this.log.debug(
                        "<res-bodies bodies={} from-node={}>",
                        blocks.size(),
                        _displayId
                );
                this.syncMgr.validateAndAddBlocks(_displayId, blocks);
            }
        } else
            this.log.error("<res-bodies decode-msg>");
    }
}
