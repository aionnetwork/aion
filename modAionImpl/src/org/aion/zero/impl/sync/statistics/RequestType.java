package org.aion.zero.impl.sync.statistics;

/**
 * Used for tracking different types of requests made to peers.
 *
 * @author Alexandra Roatis
 */
public enum RequestType {
    STATUS,
    HEADERS,
    BODIES,
    BLOCKS,
    RECEIPTS,
    TRIE_DATA
}
