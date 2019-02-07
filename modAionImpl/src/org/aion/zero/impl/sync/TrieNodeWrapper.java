package org.aion.zero.impl.sync;

import java.util.Map;
import java.util.Objects;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.zero.impl.sync.msg.ResponseTrieData;

/**
 * Container for received trie node requests.
 *
 * @author Alexandra Roatis
 */
public final class TrieNodeWrapper {

    private final int peerId;
    private final String displayId;
    private final ResponseTrieData data;

    /**
     * Constructor.
     *
     * @param peerId the hash id of the peer who sent the response
     * @param displayId the display id of the peer who sent the response
     * @param data the response received from the peer containing the trie node data
     */
    public TrieNodeWrapper(final int peerId, final String displayId, final ResponseTrieData data) {
        this.peerId = peerId;
        this.displayId = displayId;
        this.data = data;
    }

    /**
     * Returns the hash id of the peer who sent the response.
     *
     * @return the hash id of the peer who sent the response.
     */
    public int getPeerId() {
        return peerId;
    }

    /**
     * Returns the display id of the peer who sent the response.
     *
     * @return the display id of the peer who sent the response.
     */
    public String getDisplayId() {
        return displayId;
    }

    /**
     * Returns the key of the requested trie node.
     *
     * @return the key of the requested trie node.
     */
    public ByteArrayWrapper getNodeKey() {
        return data.getNodeKey();
    }

    /**
     * Returns the value stored for the requested trie node.
     *
     * @return the value stored for the requested trie node.
     */
    public byte[] getNodeValue() {
        return data.getNodeValue();
    }

    /**
     * Returns the key-value pairs for the requested trie node.
     *
     * @return the key-value pairs for the requested trie node.
     * @implNote The map is not immutable. One should avoid wrapping the same object multiple times.
     */
    public Map<ByteArrayWrapper, byte[]> getReferencedNodes() {
        return data.getReferencedNodes();
    }

    /**
     * Returns the blockchain database in which the requested key was found.
     *
     * @return the blockchain database in which the requested key was found.
     */
    public DatabaseType getDbType() {
        return data.getDbType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TrieNodeWrapper that = (TrieNodeWrapper) o;
        return peerId == that.peerId
                && Objects.equals(displayId, that.displayId)
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, displayId, data);
    }
}
