package org.aion.zero.impl.sync.handler;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Ver;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.impl.sync.statistics.RequestType;
import org.slf4j.Logger;

/** @author chris handler for block headers response from network */
public final class ResBlocksHeadersHandler extends Handler {

    private final Logger log;
    private final Logger surveyLog;

    private final SyncMgr syncMgr;

    public ResBlocksHeadersHandler(final Logger syncLog, final Logger surveyLog, final SyncMgr _syncMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_HEADERS);
        this.log = syncLog;
        this.surveyLog = surveyLog;
        this.syncMgr = _syncMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        // for runtime survey information
        long startTime, duration;

        if (_msgBytes == null || _msgBytes.length == 0) return;

        startTime = System.nanoTime();
        ResBlocksHeaders resHeaders = ResBlocksHeaders.decode(_msgBytes, log);
        duration = System.nanoTime() - startTime;
        surveyLog.debug("Receive Stage 2: decode headers, duration = {} ns.", duration);

        startTime = System.nanoTime();
        if (resHeaders != null) {
            syncMgr.getSyncStats().updateResponseTime(_displayId, System.nanoTime(), RequestType.HEADERS);
            syncMgr.validateAndAddHeaders(_nodeIdHashcode, _displayId, resHeaders.getHeaders());
        } else {
            log.error("<receive-headers: decode error msg-bytes={} node={}>", _msgBytes.length, _displayId);
            log.trace("<receive-headers: decode error dump: {}>", ByteUtil.toHexString(_msgBytes));
        }
        duration = System.nanoTime() - startTime;
        surveyLog.debug("Receive Stage 3: validate headers, duration = {} ns.", duration);
    }
}
