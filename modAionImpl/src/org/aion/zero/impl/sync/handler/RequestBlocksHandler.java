package org.aion.zero.impl.sync.handler;

import java.util.Arrays;
import java.util.List;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.RequestBlocks;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/**
 * Handler for block range requests from the network.
 *
 * @author Alexandra Roatis
 */
public final class RequestBlocksHandler extends Handler {

    private final Logger log;

    private final IAionBlockchain chain;

    private final IP2pMgr p2p;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param chain the blockchain used by the application
     * @param p2p peer manager used to submit messages
     */
    public RequestBlocksHandler(final Logger log, final IAionBlockchain chain, final IP2pMgr p2p) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_BLOCKS);
        this.log = log;
        this.chain = chain;
        this.p2p = p2p;
    }

    @Override
    public void receive(int peerId, String displayId, final byte[] message) {
        if (message == null || message.length == 0) {
            this.log.debug("<request-blocks empty message from peer={}>", displayId);
            return;
        }

        RequestBlocks request = RequestBlocks.decode(message);

        if (request != null) {
            long start = request.getStart();
            int count = request.getCount();
            boolean descending = request.isDescending();

            if (log.isDebugEnabled()) {
                this.log.debug(
                        "<request-blocks from-block={} count={} order={}>",
                        start,
                        count,
                        descending ? "DESC" : "ASC");
            }

            List<AionBlock> blockList = null;
            try {
                // retrieve blocks from block store depending on requested order
                if (descending) {
                    blockList = chain.getBlocksByRange(start, start - count + 1);
                } else {
                    blockList = chain.getBlocksByRange(start, start + count - 1);
                }
            } catch (Exception e) {
                this.log.error("<request-blocks value retrieval failed>", e);
            }

            if (blockList != null) {
                // generate response with retrieved blocks
                ResponseBlocks response = new ResponseBlocks(blockList);
                // reply to request
                this.p2p.send(peerId, displayId, response);
            }
        } else {
            this.log.error(
                    "<request-blocks decode-error msg-bytes={} peer={}>", message.length, peerId);

            if (log.isTraceEnabled()) {
                this.log.trace(
                        "<request-blocks decode-error for msg={} peer={}>",
                        Arrays.toString(message),
                        peerId);
            }
        }
    }
}
