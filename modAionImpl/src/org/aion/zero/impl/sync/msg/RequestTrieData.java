package org.aion.zero.impl.sync.msg;

import static org.aion.p2p.V1Constants.HASH_SIZE;
import static org.aion.p2p.V1Constants.TRIE_DATA_REQUEST_COMPONENTS;

import java.math.BigInteger;
import java.util.Objects;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.DatabaseType;

/**
 * Request message for a trie node from a specific blockchain database.
 *
 * @author Alexandra Roatis
 */
public final class RequestTrieData extends Msg {
    private final DatabaseType dbType;
    private final byte[] nodeKey;
    private final int limit;

    /**
     * Constructor for trie node requests with specified limit.
     *
     * @param nodeKey the key of the requested trie node
     * @param dbType the blockchain database in which the key should be found
     * @param limit the maximum number of key-value pairs to be retrieved by the search inside the
     *     trie for referenced nodes
     * @throws NullPointerException if either of the given parameters is {@code null}
     * @throws IllegalArgumentException if the given limit is negative
     */
    public RequestTrieData(final byte[] nodeKey, final DatabaseType dbType, final int limit) {
        super(Ver.V1, Ctrl.SYNC, Act.REQUEST_TRIE_DATA);

        // ensure inputs are not null
        Objects.requireNonNull(nodeKey);
        Objects.requireNonNull(dbType);

        // ensure limit is positive
        if (limit < 0) {
            throw new IllegalArgumentException(
                    "The RequestTrieData object must be built with a positive limit.");
        }

        this.nodeKey = nodeKey;
        this.dbType = dbType;
        this.limit = limit;
    }

    /**
     * Decodes a message into a trie node request.
     *
     * @param message a {@code byte} array representing a request for a trie node.
     * @return the decoded trie node request if valid or {@code null} when the decoding encounters
     *     invalid input
     * @implNote Ensures that the components are not {@code null}.
     */
    public static RequestTrieData decode(final byte[] message) {
        if (message == null || message.length == 0) {
            return null;
        } else {
            RLPList list = (RLPList) RLP.decode2(message).get(0);
            if (list.size() != TRIE_DATA_REQUEST_COMPONENTS) {
                return null;
            } else {
                // decode the key
                byte[] hash = list.get(0).getRLPData();
                if (hash.length != HASH_SIZE) {
                    return null;
                }

                // decode the db type
                byte[] type = list.get(1).getRLPData();
                DatabaseType dbType;
                try {
                    dbType = DatabaseType.valueOf(new String(type));
                } catch (IllegalArgumentException e) {
                    return null;
                }

                // decode the limit
                int depth = new BigInteger(1, list.get(2).getRLPData()).intValue();

                return new RequestTrieData(hash, dbType, depth);
            }
        }
    }

    @Override
    public byte[] encode() {
        return RLP.encodeList(
                RLP.encodeElement(nodeKey),
                RLP.encodeString(dbType.toString()),
                RLP.encodeInt(limit));
    }

    /**
     * Returns the blockchain database in which the requested key should be found.
     *
     * @return the blockchain database in which the requested key should be found
     */
    public DatabaseType getDbType() {
        return dbType;
    }

    /**
     * Returns the key of the requested trie node.
     *
     * @return the key of the requested trie node
     */
    public byte[] getNodeKey() {
        return nodeKey;
    }

    /**
     * Returns the maximum number of key-value pairs to be retrieved by the search inside the trie
     * for referenced nodes, where:
     *
     * <ul>
     *   <li>zero stands for searching for referenced nodes without a limit on the number of nodes
     *       retrieved;
     *   <li>one stands for not searching beyond the retrieved value for the given key;
     *   <li>a positive value greater than one represents the number of additional key-value pairs
     *       up to which to continue searching for referenced nodes.
     * </ul>
     *
     * @return the maximum number of key-value pairs to be retrieved by the search inside the trie
     *     for referenced nodes
     */
    public int getLimit() {
        return limit;
    }
}
