package org.aion.zero.impl.sync.handler;

import java.util.List;
import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.RequestType;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.slf4j.Logger;

/**
 * Handler for blocks bodies received from network.
 *
 * @author chris
 */
public final class ResBlocksBodiesHandler extends Handler {

    private final Logger log;

    private final SyncMgr syncMgr;

    private final IP2pMgr p2pMgr;

    public ResBlocksBodiesHandler(
            final Logger _log, final SyncMgr _syncMgr, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        this.log = _log;
        this.syncMgr = _syncMgr;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        ResBlocksBodies resBlocksBodies = ResBlocksBodies.decode(_msgBytes);
        List<byte[]> bodies = resBlocksBodies.getBlocksBodies();
        if (bodies == null) {
            log.error("<res-bodies decoder-error from {}, len: {]>", _displayId, _msgBytes.length);
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            if (log.isTraceEnabled()) {
                log.trace("res-bodies dump: {}", ByteUtil.toHexString(_msgBytes));
            }

        } else {
            this.syncMgr
                    .getSyncStats()
                    .updateResponseTime(_displayId, System.nanoTime(), RequestType.BODIES);

            if (bodies.isEmpty()) {
                p2pMgr.errCheck(_nodeIdHashcode, _displayId);
                log.error("<res-bodies-empty node={}>", _displayId);
            } else {
                syncMgr.getSyncStats().updatePeerTotalBlocks(_displayId, bodies.size());
                syncMgr.validateAndAddBlocks(_nodeIdHashcode, _displayId, bodies);
            }
        }
    }
}
