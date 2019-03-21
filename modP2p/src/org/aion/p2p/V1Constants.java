package org.aion.p2p;

/**
 * Constants used by {@link Ver#V1} which introduces functionality for fast sync.
 *
 * @author Alexandra Roatis
 */
public class V1Constants {

    /** The size of the hashes used in the trie implementation. */
    public static int HASH_SIZE = 32;

    /** The number of components contained in a trie data request. */
    public static int TRIE_DATA_REQUEST_COMPONENTS = 3;

    /** Limits the number of key-value pairs returned to one trie data request. */
    public static final int TRIE_DATA_REQUEST_MAXIMUM_BATCH_SIZE = 100;

    /** Limits the number of blocks returned to one blocks request. */
    // TODO: also add limit for the size of the resulting message
    public static final int BLOCKS_REQUEST_MAXIMUM_BATCH_SIZE = 60;

    /** The number of components contained in a trie data response. */
    public static int TRIE_DATA_RESPONSE_COMPONENTS = 4;
}
