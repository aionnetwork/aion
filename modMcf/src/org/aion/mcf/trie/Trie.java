package org.aion.mcf.trie;

import java.util.Map;
import java.util.Set;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteArrayWrapper;

/**
 * Trie interface for the main data structure in Ethereum which is used to store both the account
 * state and storage of each account.
 */
public interface Trie {

    /**
     * Gets a value from the trie for a given key
     *
     * @param key - any length byte array
     * @return an rlp encoded byte array of the stored object
     */
    byte[] get(byte[] key);

    /**
     * Insert or update a value in the trie for a specified key
     *
     * @param key - any length byte array
     * @param value rlp encoded byte array of the object to store
     */
    void update(byte[] key, byte[] value);

    /**
     * Deletes a key/value from the trie for a given key
     *
     * @param key - any length byte array
     */
    void delete(byte[] key);

    /**
     * Returns a SHA-3 hash from the top node of the trie
     *
     * @return 32-byte SHA-3 hash representing the entire contents of the trie.
     */
    byte[] getRootHash();

    /**
     * Set the top node of the trie
     *
     * @param root - 32-byte SHA-3 hash of the root node
     */
    void setRoot(byte[] root);

    /**
     * Used to check for corruption in the database.
     *
     * @param root a world state trie root
     * @return {@code true} if the root is valid, {@code false} otherwise
     */
    boolean isValidRoot(byte[] root);

    /** Commit all the changes until now */
    void sync();

    void sync(boolean flushCache);

    // never used
    //    /** Discard all the changes until now */
    //    @Deprecated
    //    void undo();

    String getTrieDump();

    String getTrieDump(byte[] stateRoot);

    int getTrieSize(byte[] stateRoot);

    // never used
    //    boolean validate();

    /**
     * Traverse the trie starting from the given node. Return the keys for all the missing branches
     * that are encountered during the traversal.
     *
     * @param key the starting node for the trie traversal
     * @return a set of keys that were referenced as part of the trie but could not be found in the
     *     database
     */
    Set<ByteArrayWrapper> getMissingNodes(byte[] key);

    /**
     * Retrieves nodes referenced by a trie node value, where the size of the result is bounded by
     * the given limit.
     *
     * @param value a trie node value which may be referencing other nodes
     * @param limit the maximum number of key-value pairs to be retrieved by this method, which
     *     limits the search in the trie; zero and negative values for the limit will result in no
     *     search and an empty map will be returned
     * @return an empty map when the value does not reference other trie nodes or the given limit is
     *     invalid, or a map containing all the referenced nodes reached while keeping within the
     *     limit on the result size
     */
    Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(byte[] value, int limit);

    long saveFullStateToDatabase(byte[] stateRoot, IByteArrayKeyValueDatabase db);

    long saveDiffStateToDatabase(byte[] stateRoot, IByteArrayKeyValueDatabase db);
}
