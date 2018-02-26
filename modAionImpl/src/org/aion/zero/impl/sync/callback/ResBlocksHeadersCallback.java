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

import org.aion.p2p.CTRL;
import org.aion.p2p.ICallback;
import org.aion.zero.impl.sync.ACT;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * 
 * @author chris
 *
 */

public final class ResBlocksHeadersCallback implements ICallback {

    private final static byte ctrl = CTRL.SYNC0;

    private final static byte act = ACT.RES_BLOCKS_HEADERS;

    private final SyncMgr syncMgr;

    private final Logger log;

    public ResBlocksHeadersCallback(final Logger _log, final SyncMgr _syncMgr) {
        this.syncMgr = _syncMgr;
        this.log = _log;
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
        if (_msgBytes == null || _msgBytes.length == 0)
            return;
        ResBlocksHeaders resHeaders = ResBlocksHeaders.decode(_msgBytes);
        if (resHeaders != null) {
            List<A0BlockHeader> headers = resHeaders.getHeaders();
            if (headers.size() > 0) {
                this.log.debug("<res-headers from-block={} take={} from-node={}>", headers.get(0).getNumber(),
                        headers.size(), java.util.Arrays.hashCode(_nodeId));
                this.syncMgr.validateAndAddHeaders(_nodeId, headers);
            }
        } else
            this.log.error("<res-headers decode-msg msg-bytes={} from-node={} >",
                    _msgBytes == null ? 0 : _msgBytes.length, java.util.Arrays.hashCode(_nodeId));
    }
}
