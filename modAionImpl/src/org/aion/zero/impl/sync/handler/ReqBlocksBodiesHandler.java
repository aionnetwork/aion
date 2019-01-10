package org.aion.zero.impl.sync.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.P2pConstant;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.SyncMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.impl.types.AionBlock;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

/** @author chris handler for request block bodies broadcasted from network */
public final class ReqBlocksBodiesHandler extends Handler {

    private static final int MAX_NUM_OF_BLOCKS = 96;

    private final Logger log;

    private final IAionBlockchain blockchain;

    private final SyncMgr syncMgr;

    private final IP2pMgr p2pMgr;

    private final Map<ByteArrayWrapper, byte[]> cache =
            Collections.synchronizedMap(new LRUMap<>(512));

    private final boolean isSyncOnlyNode;

    public ReqBlocksBodiesHandler(
            final Logger _log,
            final IAionBlockchain _blockchain,
            final SyncMgr _syncMgr,
            final IP2pMgr _p2pMgr,
            final boolean isSyncOnlyNode) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_BLOCKS_BODIES);
        this.log = _log;
        this.blockchain = _blockchain;
        this.syncMgr = _syncMgr;
        this.p2pMgr = _p2pMgr;
        this.isSyncOnlyNode = isSyncOnlyNode;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (isSyncOnlyNode) return;

        ReqBlocksBodies reqBlocks = ReqBlocksBodies.decode(_msgBytes);
        if (reqBlocks != null) {

            // limit number of blocks
            List<byte[]> hashes = reqBlocks.getBlocksHashes();
            hashes =
                    hashes.size() > MAX_NUM_OF_BLOCKS
                            ? hashes.subList(0, MAX_NUM_OF_BLOCKS)
                            : hashes;

            // results
            List<byte[]> blockBodies = new ArrayList<>();

            // read from cache, then block store
            int out = 0;
            for (byte[] hash : hashes) {

                // ref for add.
                byte[] blockBytesForadd;

                byte[] blockBytes = cache.get(ByteArrayWrapper.wrap(hash));

                // if cached , add.
                if (blockBytes != null) {
                    blockBytesForadd = blockBytes;
                } else {
                    AionBlock block = blockchain.getBlockByHash(hash);

                    if (block != null) {
                        blockBytesForadd = block.getEncodedBody();
                        cache.put(ByteArrayWrapper.wrap(hash), block.getEncodedBody());
                    } else {
                        // not found
                        break;
                    }
                }

                if ((out += blockBytesForadd.length) > P2pConstant.MAX_BODY_SIZE) {
                    log.debug(
                            "<req-blocks-bodies-max-size-reach size={}/{}>",
                            out,
                            P2pConstant.MAX_BODY_SIZE);
                    break;
                }

                blockBodies.add(blockBytesForadd);
            }

            this.p2pMgr.send(_nodeIdHashcode, _displayId, new ResBlocksBodies(blockBodies));
            this.syncMgr
                    .getSyncStats()
                    .updateTotalBlockRequestsByPeer(_displayId, blockBodies.size());

            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<req-bodies req-size={} res-size={} node={}>",
                        reqBlocks.getBlocksHashes().size(),
                        blockBodies.size(),
                        _displayId);
            }
        } else {

            this.log.error(
                    "<req-bodies decode-error, unable to decode bodies from {}, len: {}>",
                    _displayId,
                    _msgBytes.length);

            if (this.log.isTraceEnabled()) {
                this.log.trace("req-bodies dump: {}", ByteUtil.toHexString(_msgBytes));
            }
        }
    }
}
