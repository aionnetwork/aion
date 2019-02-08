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

    /** The number of components contained in a trie data response. */
    public static int TRIE_DATA_RESPONSE_COMPONENTS = 4;
}
