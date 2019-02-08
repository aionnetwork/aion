package org.aion.zero.impl.sync.handler;

import static org.aion.p2p.V1Constants.TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE;

import java.util.Map;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.sync.msg.RequestTrieData;
import org.aion.zero.impl.sync.msg.ResponseTrieData;
import org.slf4j.Logger;

/**
 * Handler for trie node requests from the network.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieDataHandler extends Handler {

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
    public RequestTrieDataHandler(
            final Logger log, final IAionBlockchain chain, final IP2pMgr p2p) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_DATA);
        this.log = log;
        this.chain = chain;
        this.p2p = p2p;
    }

    @Override
    public void receive(int peerId, String displayId, final byte[] message) {
        if (message == null || message.length == 0) {
            this.log.debug("<req-trie empty message from peer={}>", displayId);
            return;
        }

        RequestTrieData request = RequestTrieData.decode(message);

        if (request != null) {
            DatabaseType dbType = request.getDbType();
            ByteArrayWrapper key = ByteArrayWrapper.wrap(request.getNodeKey());
            int limit = request.getLimit();

            if (log.isDebugEnabled()) {
                this.log.debug("<req-trie from-db={} key={} peer={}>", dbType, key, displayId);
            }

            // retrieve from blockchain depending on db type
            byte[] value = chain.getTrieNode(key.getData(), dbType);

            if (value != null) {
                ResponseTrieData response;

                if (limit == 1) {
                    // generate response without referenced nodes
                    response = new ResponseTrieData(key, value, dbType);
                } else {
                    // check for internal limit on the request
                    if (limit == 0) {
                        limit = TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE;
                    } else {
                        // the first value counts towards the limit
                        limit--;
                        limit =
                                limit < TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE
                                        ? limit
                                        : TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE;
                    }

                    // determine if the node can be expanded
                    Map<ByteArrayWrapper, byte[]> referencedNodes =
                            chain.getReferencedTrieNodes(value, limit, dbType);

                    // generate response with referenced nodes
                    response = new ResponseTrieData(key, value, referencedNodes, dbType);
                }

                // reply to request
                this.p2p.send(peerId, displayId, response);
            }
        } else {
            this.log.error("<req-trie decode-error msg-bytes={} peer={}>", message.length, peerId);
        }
    }
}
