package org.aion.zero.impl.sync;

import org.aion.zero.impl.sync.statistics.RequestType;

/**
 * Used for tracking different types of requests made to peers.
 *
 * @author Alexandra Roatis
 */
public class RequestCounter {

    private long status = 0;
    private long headers = 0;
    private long bodies = 0;
    private long blocks = 0;
    private long receipts = 0;
    private long trieData = 0;
    private long total = 0;

    public RequestCounter(RequestType type) {
        switch (type) {
            case STATUS:
                incStatus();
                break;
            case HEADERS:
                incHeaders();
                break;
            case BODIES:
                incBodies();
                break;
            case BLOCKS:
                incBlocks();
                break;
            case RECEIPTS:
                incRecepts();
                break;
            case TRIE_DATA:
                incTrieData();
                break;
        }
    }

    public long getStatus() {
        return status;
    }

    public long getHeaders() {
        return headers;
    }

    public long getBodies() {
        return bodies;
    }

    public long getBlocks() {
        return blocks;
    }

    public long getReceipts() {
        return receipts;
    }

    public long getTrieData() {
        return trieData;
    }

    public long getTotal() {
        return total;
    }

    public void incStatus() {
        this.status++;
        this.total++;
    }

    public void incHeaders() {
        this.headers++;
        this.total++;
    }

    public void incBodies() {
        this.bodies++;
        this.total++;
    }

    public void incBlocks() {
        this.blocks++;
        this.total++;
    }

    public void incRecepts() {
        this.receipts++;
        this.total++;
    }

    public void incTrieData() {
        this.trieData++;
        this.total++;
    }
}
