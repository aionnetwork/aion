package org.aion.mcf.trie;

/**
 * Possible results after importing a trie node.
 *
 * @author Alexandra Roatis
 */
public enum TrieNodeResult {
    /** Successful import. */
    IMPORTED,
    /** The key-value pair is already stored. */
    KNOWN,
    /** The key is already stored but the value does not match the given one. */
    INCONSISTENT,
    /** The key is not valid. */
    INVALID_KEY,
    /** The value is not valid. */
    INVALID_VALUE;

    public boolean isSuccessful() {
        return equals(IMPORTED) || equals(KNOWN);
    }
}
