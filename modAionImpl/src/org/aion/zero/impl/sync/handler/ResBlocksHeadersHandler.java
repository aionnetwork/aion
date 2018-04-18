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
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

import java.util.List;

/**
 *
 * @author chris
 * handler for block headers response from network
 *
 */
public final class ResBlocksHeadersHandler extends Handler {

    private final Logger log;

    private final SyncMgr syncMgr;

    private final IP2pMgr p2pMgr;

    public ResBlocksHeadersHandler(final Logger _log, final SyncMgr _syncMgr, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_HEADERS);
        this.syncMgr = _syncMgr;
        this.log = _log;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if(_msgBytes == null || _msgBytes.length == 0)
            return;
        ResBlocksHeaders resHeaders = ResBlocksHeaders.decode(_msgBytes);
        if(resHeaders != null) {
            List<A0BlockHeader> headers = resHeaders.getHeaders();
            if(headers != null && headers.size() > 0){
                if (log.isDebugEnabled()) {
                    this.log.debug("<res-headers from-number={} size={} node={}>", headers.get(0).getNumber(), headers.size(),
                            _displayId);
                }
                this.syncMgr.validateAndAddHeaders(_nodeIdHashcode, _displayId, headers);
            } else {
                p2pMgr.errCheck(_nodeIdHashcode, _displayId);
                this.log.error(
                    "<res-headers empty-headers node={} >",
                    _displayId
                );
            }
        } else {
            //p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            this.log.error(
                    "<res-headers decode-error msg-bytes={} node={}>",
                    _msgBytes.length,
                    _displayId
            );

            if (this.log.isTraceEnabled()) {
                this.log.trace("res-headers decode-error dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }
    }
}