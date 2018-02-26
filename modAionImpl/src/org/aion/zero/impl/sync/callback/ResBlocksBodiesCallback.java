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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.aion.p2p.ICallback;
import org.aion.p2p.CTRL;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * @author chris
 */

public final class ResBlocksBodiesCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_BLOCKS_BODIES;

    private final Logger log;

    private final SyncMgr syncMgr;

    public ResBlocksBodiesCallback(final Logger _log, final SyncMgr _syncMgr) {
        this.log = _log;
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
        ResBlocksBodies resBlocksBodies = ResBlocksBodies.decode(_msgBytes);
        List<A0BlockHeader> headers = this.syncMgr.getSentHeaders(_nodeId);
        List<byte[]> bodies = resBlocksBodies.getBlocksBodies();
        if (headers != null && bodies != null && headers.size() == bodies.size()) {
            List<AionBlock> blocks = new ArrayList<>(bodies.size());
            Iterator<A0BlockHeader> headerIt = headers.iterator();
            Iterator<byte[]> bodyIt = bodies.iterator();
            boolean pass = true;
            mergeHeaderAndBody: while (headerIt.hasNext() && bodyIt.hasNext()) {
                AionBlock block = AionBlock.createBlockFromNetwork(headerIt.next(), bodyIt.next());
                if (block == null) {
                    pass = false;
                    break mergeHeaderAndBody;
                } else
                    blocks.add(block);
            }
            this.syncMgr.clearSentHeaders(_nodeId);
            if (pass) {
                this.log.debug("<res-bodies bodies={} from-node={}>", blocks.size(),
                        java.util.Arrays.hashCode(_nodeId));
                this.syncMgr.validateAndAddBlocks(_nodeId, blocks, false);
            }
        } else
            this.log.error("<res-bodies decode-msg>");
    }
}
