package org.aion.zero.impl.sync;

import java.util.Collections;
import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;

/**
 * A container used to package together the block headers received from a peer with the peer's
 * identification information.
 */
final class HeadersWrapper {
    public final int nodeId;
    public final String displayId;
    public final long timestamp;
    public final List<BlockHeader> headers;
    public final int size;

    /**
     * A container for received block headers and peer information.
     *
     * @param nodeId the peer's node identifier
     * @param displayId the peer's display identifier
     * @param headers the received blocks
     */
    HeadersWrapper(int nodeId, String displayId, final List<BlockHeader> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one block header in the given list.");
        }
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.headers = Collections.unmodifiableList(headers);
        this.size = headers.size();
        this.timestamp = System.currentTimeMillis();
    }
}
