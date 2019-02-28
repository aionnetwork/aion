package org.aion.zero.impl.sync.handler;

import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.RequestType;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/** @author chris handler for block headers response from network */
public final class ResBlocksHeadersHandler extends Handler {

    private final Logger log;

    private final SyncMgr syncMgr;

    private final IP2pMgr p2pMgr;

    public ResBlocksHeadersHandler(
            final Logger _log, final SyncMgr _syncMgr, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_HEADERS);
        this.syncMgr = _syncMgr;
        this.log = _log;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0) return;
        ResBlocksHeaders resHeaders = ResBlocksHeaders.decode(_msgBytes);
        if (resHeaders != null) {

            this.syncMgr
                    .getSyncStats()
                    .updateResponseTime(_displayId, System.nanoTime(), RequestType.HEADERS);

            List<A0BlockHeader> headers = resHeaders.getHeaders();
            if (headers != null && headers.size() > 0) {
                if (log.isDebugEnabled()) {
                    this.log.debug(
                            "<res-headers from-number={} size={} node={}>",
                            headers.get(0).getNumber(),
                            headers.size(),
                            _displayId);
                }
                this.syncMgr.validateAndAddHeaders(_nodeIdHashcode, _displayId, headers);
            } else {
                p2pMgr.errCheck(_nodeIdHashcode, _displayId);
                this.log.error("<res-headers empty-headers node={} >", _displayId);
            }
        } else {
            // p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            this.log.error(
                    "<res-headers decode-error msg-bytes={} node={}>",
                    _msgBytes.length,
                    _displayId);

            if (this.log.isTraceEnabled()) {
                this.log.trace(
                        "res-headers decode-error dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }
    }
}
