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

import java.util.Collections;

import org.aion.p2p.ICallback;
import org.aion.p2p.CTRL;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.BlockPropagationHandler;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * @author jay
 */
public final class BroadcastNewBlockCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.BROADCAST_NEWBLOCK;

    private final Logger log;

    private final BlockPropagationHandler propHandler;

    /*
     * (non-Javadoc)
     *
     * @see org.aion.net.nio.ICallback#getCtrl() change param
     * IPendingStateInternal later
     */
    public BroadcastNewBlockCallback(final Logger _log, final BlockPropagationHandler _propHandler) {
        this.log = _log;
        this.propHandler = _propHandler;
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
        if (_msgBytes == null)
            return;
        byte[] rawdata = BroadcastNewBlock.decode(_msgBytes);
        if (rawdata == null)
            return;

        AionBlock block = new AionBlock(rawdata);

        if (this.log.isInfoEnabled()) {
            this.log.info("<receive-broadcast-new-block num={} hash={} from-node={}>", block.getNumber(),
                    block.getShortHash(), java.util.Arrays.hashCode(_nodeId));
        }
        BlockPropagationHandler.Status status = this.propHandler.processIncomingBlock(_nodeId, block);
        if (!(status == BlockPropagationHandler.Status.CONNECTED)) {
            if (this.log.isDebugEnabled()) {
                String hash = block.getShortHash();
                hash = hash != null ? hash : "null";
                this.log.debug("block propagation status: [" + hash + " / " + status.name() + "]");
            }
        }
    }
}
