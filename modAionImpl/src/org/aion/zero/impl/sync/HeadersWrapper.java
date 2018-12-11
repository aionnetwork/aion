package org.aion.zero.impl.sync;

import java.util.List;
import org.aion.zero.types.A0BlockHeader;

/** @author chris used by imported headers on sync mgr */
final class HeadersWrapper {

    private int nodeIdHash;

    private String displayId;

    private long timestamp;

    private List<A0BlockHeader> headers;

    /**
     * @param _nodeIdHash int
     * @param _headers List
     */
    HeadersWrapper(int _nodeIdHash, String _displayId, final List<A0BlockHeader> _headers) {
        this.nodeIdHash = _nodeIdHash;
        this.displayId = _displayId;
        this.headers = _headers;
        this.timestamp = System.currentTimeMillis();
    }

    /** @return int - node id hash */
    int getNodeIdHash() {
        return this.nodeIdHash;
    }

    /** @return String - node display id */
    String getDisplayId() {
        return this.displayId;
    }

    /** @return long used to compare and drop from queue if expired */
    long getTimestamp() {
        return this.timestamp;
    }

    /** @return List */
    List<A0BlockHeader> getHeaders() {
        return this.headers;
    }
}
