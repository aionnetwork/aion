package org.aion.zero.impl.sync.handler;

import java.util.Arrays;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.FastSyncManager;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.slf4j.Logger;

/**
 * Handler for block range responses from the network.
 *
 * @author Alexandra Roatis
 */
public final class ResponseBlocksHandler extends Handler {

    private final Logger log;

    private final FastSyncManager fastSyncMgr;

    private final IP2pMgr p2pMgr;

    /**
     * Constructor.
     *
     * @param log logger for reporting execution information
     * @param fastSyncMgr sync manager that can validate blocks and pass them further for importing
     * @param p2pMgr p2p manager that can check for errors with the peer identifiers
     */
    public ResponseBlocksHandler(
            final Logger log, final FastSyncManager fastSyncMgr, final IP2pMgr p2pMgr) {
        super(Ver.V1, Ctrl.SYNC, Act.RESPONSE_TRIE_DATA);
        this.log = log;
        this.fastSyncMgr = fastSyncMgr;
        this.p2pMgr = p2pMgr;
    }

    @Override
    public void receive(int peerId, String displayId, final byte[] message) {
        if (message == null || message.length == 0) {
            p2pMgr.errCheck(peerId, displayId);
            log.debug("<response-blocks empty message from peer={}>", displayId);
            return;
        }

        ResponseBlocks response = ResponseBlocks.decode(message);

        if (response != null) {
            if (log.isDebugEnabled()) {
                log.debug("<response-blocks response={} peer={}>", response, displayId);
            }

            // checks PoW and adds correct blocks to import list
            fastSyncMgr.validateAndAddBlocks(peerId, displayId, response);
        } else {
            p2pMgr.errCheck(peerId, displayId);
            log.error(
                    "<response-blocks decode-error msg-bytes={} peer={}>", message.length, peerId);

            if (log.isTraceEnabled()) {
                log.trace(
                        "<response-blocks decode-error for msg={} peer={}>",
                        Arrays.toString(message),
                        peerId);
            }
        }
    }
}
