package org.aion.zero.impl.sync.handler;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * handler for status request from network
 *
 * @author chris
 */
public final class ReqStatusHandler extends Handler {

    private final Logger log;

    private IAionBlockchain chain;

    private IP2pMgr mgr;

    private byte[] genesisHash;

    private final int UPDATE_INTERVAL = 500;

    private volatile ResStatus cache;

    private volatile long cacheTs = 0;

    public ReqStatusHandler(
            final Logger _log,
            final IAionBlockchain _chain,
            final IP2pMgr _mgr,
            final byte[] _genesisHash) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_STATUS);
        this.log = _log;
        this.chain = _chain;
        this.mgr = _mgr;
        this.genesisHash = _genesisHash;
        this.cache = new ResStatus(0, new byte[0], new byte[0], _genesisHash);
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, byte[] _msg) {
        long now = System.currentTimeMillis();
        if ((now - cacheTs) > this.UPDATE_INTERVAL) {
            synchronized (cache) {
                try {
                    AionBlock bestBlock = chain.getBestBlock();
                    cache =
                            new ResStatus(
                                    bestBlock.getNumber(),
                                    bestBlock.getCumulativeDifficulty().toByteArray(),
                                    bestBlock.getHash(),
                                    this.genesisHash);
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug("ReqStatus exception {}", e.toString());
                    }
                }
                cacheTs = now;
            }
        }

        this.mgr.send(_nodeIdHashcode, _displayId, cache);
        if (log.isDebugEnabled()) {
            this.log.debug(
                    "<req-status node={} return-blk={}>", _displayId, cache.getBestBlockNumber());
        }
    }
}
