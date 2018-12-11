package org.aion.zero.impl.sync.handler;

import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * handler for request block headers from network
 *
 * @author chris
 */
public final class ReqBlocksHeadersHandler extends Handler {

    private static final int MAX_NUM_OF_BLOCKS = 96;

    private final Logger log;

    private final IAionBlockchain blockchain;

    private final IP2pMgr p2pMgr;

    private final boolean isSyncOnlyNode;

    public ReqBlocksHeadersHandler(
            final Logger _log,
            final IAionBlockchain _blockchain,
            final IP2pMgr _p2pMgr,
            final boolean isSyncOnlyNode) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_HEADERS);
        this.log = _log;
        this.blockchain = _blockchain;
        this.p2pMgr = _p2pMgr;
        this.isSyncOnlyNode = isSyncOnlyNode;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (isSyncOnlyNode) {
            return;
        }

        ReqBlocksHeaders reqHeaders = ReqBlocksHeaders.decode(_msgBytes);
        if (reqHeaders != null) {
            long fromBlock = reqHeaders.getFromBlock();
            int take = reqHeaders.getTake();
            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<req-headers from-number={} size={} node={}>",
                        fromBlock,
                        take,
                        _displayId);
            }
            List<A0BlockHeader> headers =
                    this.blockchain.getListOfHeadersStartFrom(
                            fromBlock, Math.min(take, MAX_NUM_OF_BLOCKS));
            ResBlocksHeaders rbhs = new ResBlocksHeaders(headers);
            this.p2pMgr.send(_nodeIdHashcode, _displayId, rbhs);
        } else {
            this.log.error(
                    "<req-headers decode-error msg-bytes={} node={}>",
                    _msgBytes == null ? 0 : _msgBytes.length,
                    _nodeIdHashcode);
        }
    }
}
