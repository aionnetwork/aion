package org.aion.zero.impl.sync.handler;

import org.aion.mcf.blockchain.Block;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.BroadcastNewBlock;
import org.aion.zero.impl.types.BlockUtil;
import org.slf4j.Logger;

/** @author jay handler for new block broadcasted from network */
public final class BroadcastNewBlockHandler extends Handler {

    private final Logger log;
    private final Logger surveyLog;

    private final BlockPropagationHandler propHandler;

    private final IP2pMgr p2pMgr;

    public BroadcastNewBlockHandler(
            final Logger syncLog, final Logger surveyLog, final BlockPropagationHandler propHandler, final IP2pMgr _p2pMgr) {
        super(Ver.V0, Ctrl.SYNC, Act.BROADCAST_BLOCK);
        this.log = syncLog;
        this.surveyLog = surveyLog;
        this.propHandler = propHandler;
        this.p2pMgr = _p2pMgr;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        // for runtime survey information
        long startTime, duration;

        if (_msgBytes == null) return;

        startTime = System.nanoTime();
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
            duration = System.nanoTime() - startTime;
            surveyLog.info("Receive Stage 6: process propagated block, duration = {} ns.", duration);
            return;
        }

        try { // preventative try-catch: it's unlikely that exceptions can pass up to here
            RLPList params = RLP.decode2(rawdata);
            RLPList blockRLP = (RLPList) params.get(0);

            // returns null when decoding failed
            Block block = BlockUtil.newBlockFromRlpList(blockRLP);
            if (block != null) {
                BlockPropagationHandler.PropStatus result =
                        this.propHandler.processIncomingBlock(_nodeIdHashcode, _displayId, block);

                duration = System.nanoTime() - startTime;
                surveyLog.info("Receive Stage 6: process propagated block, duration = {} ns.", duration);

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
        } catch (Exception e) {
            log.error("RLP decode error!", e);
        }
    }
}
