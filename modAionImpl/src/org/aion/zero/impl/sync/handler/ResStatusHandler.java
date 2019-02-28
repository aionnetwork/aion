package org.aion.zero.impl.sync.handler;

import java.math.BigInteger;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.RequestType;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.slf4j.Logger;

/** @author chris */
public final class ResStatusHandler extends Handler {

    private final Logger log;

    private final IP2pMgr p2pMgr;

    private final SyncMgr syncMgr;

    public ResStatusHandler(final Logger _log, final IP2pMgr _p2pMgr, final SyncMgr _syncMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_STATUS);
        this.log = _log;
        this.p2pMgr = _p2pMgr;
        this.syncMgr = _syncMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0) return;
        ResStatus rs = ResStatus.decode(_msgBytes);

        if (rs == null) {
            this.log.error(
                    "<res-status decode-error from {} len: {}>", _displayId, _msgBytes.length);
            if (this.log.isTraceEnabled()) {
                this.log.trace("res-status decode-error dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }

        this.syncMgr
                .getSyncStats()
                .updateResponseTime(_displayId, System.nanoTime(), RequestType.STATUS);
        this.syncMgr.getSyncStats().updatePeerTotalBlocks(_displayId, 1);

        INode node = this.p2pMgr.getActiveNodes().get(_nodeIdHashcode);
        if (node != null && rs != null) {
            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<res-status node={} best-blk={}>", _displayId, rs.getBestBlockNumber());
            }
            long remoteBestBlockNumber = rs.getBestBlockNumber();
            byte[] remoteBestBlockHash = rs.getBestHash();
            byte[] remoteTdBytes = rs.getTotalDifficulty();
            byte apiVersion = rs.getApiVersion();
            short peerCount = rs.getPeerCount();
            byte[] pendingTcBytes = rs.getPendingTxCount();
            int latency = rs.getLatency();
            if (remoteTdBytes != null && remoteBestBlockHash != null) {
                BigInteger remoteTotalDifficulty = new BigInteger(1, remoteTdBytes);
                int pendingTxCount = new BigInteger(1, pendingTcBytes).intValue();
                node.updateStatus(
                        remoteBestBlockNumber,
                        remoteBestBlockHash,
                        remoteTotalDifficulty,
                        apiVersion,
                        peerCount,
                        pendingTxCount,
                        latency);
                syncMgr.updateNetworkStatus(
                        _displayId,
                        remoteBestBlockNumber,
                        remoteBestBlockHash,
                        remoteTotalDifficulty,
                        apiVersion,
                        peerCount,
                        pendingTxCount,
                        latency);
            }
        }
    }
}
