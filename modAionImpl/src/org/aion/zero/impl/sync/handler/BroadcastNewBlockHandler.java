package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/** @author jay handler for new block broadcasted from network */
public final class BroadcastNewBlockHandler extends Handler {

    private final Logger log;

    private final BlockPropagationHandler propHandler;

    private final IP2pMgr p2pMgr;

    public BroadcastNewBlockHandler(
            final Logger _log, final BlockPropagationHandler propHandler, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_BLOCK);
        this.log = _log;
        this.propHandler = propHandler;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null) return;
        byte[] rawdata = BroadcastNewBlock.decode(_msgBytes);
        if (rawdata == null) {
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);
            log.error(
                    "<new-block-handler decode-error, from {} len: {}>",
                    _displayId,
                    _msgBytes.length);
            if (log.isTraceEnabled()) {
                log.trace("new-block-handler dump: {}", ByteUtil.toHexString(_msgBytes));
            }
            return;
        }

        AionBlock block = new AionBlock(rawdata);

        BlockPropagationHandler.PropStatus result =
                this.propHandler.processIncomingBlock(_nodeIdHashcode, _displayId, block);

        if (this.log.isDebugEnabled()) {
            String hash = block.getShortHash();
            hash = hash != null ? hash : "null";
            this.log.debug(
                    "<block-prop node="
                            + _displayId
                            + " block-hash="
                            + hash
                            + " status="
                            + result.name()
                            + ">");
        }
    }
}
