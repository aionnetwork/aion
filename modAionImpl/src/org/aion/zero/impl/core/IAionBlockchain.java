package org.aion.zero.impl.core;

import java.util.List;
import java.util.Map;
import org.aion.interfaces.db.Repository;
import org.aion.mcf.core.IBlockchain;
import org.aion.types.ByteArrayWrapper;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

/** aion blockchain interface. */
public interface IAionBlockchain
        extends IBlockchain<AionBlock, A0BlockHeader, AionTransaction, AionTxReceipt, AionTxInfo> {

    AionBlock createNewBlock(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    BlockContext createNewBlockContext(
            AionBlock parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    AionBlock getBestBlock();

    AionBlock getBlockByNumber(long num);

    /**
     * Recovery functionality for rebuilding the world state.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverWorldState(Repository repository, AionBlock block);

    /**
     * Recovery functionality for recreating the block info in the index database.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverIndexEntry(Repository repository, AionBlock block);

    /**
     * Heuristic for skipping the call to tryToConnect with very large or very small block number.
     */
    boolean skipTryToConnect(long blockNumber);

    /**
     * Retrieves the value for a given node from the database associated with the given type.
     *
     * @param key the key of the node to be retrieved
     * @param dbType the database where the key should be found
     * @return the {@code byte} array value associated with the given key or {@code null} when the
     *     key cannot be found in the database
     * @throws IllegalArgumentException if the given key is null or the database type is not
     *     supported
     */
    byte[] getTrieNode(byte[] key, DatabaseType dbType);

    /**
     * Retrieves nodes referenced by a trie node value, where the size of the result is bounded by
     * the given limit.
     *
     * @param value a trie node value which may be referencing other nodes
     * @param limit the maximum number of key-value pairs to be retrieved by this method, which
     *     limits the search in the trie; zero and negative values for the limit will result in no
     *     search and an empty map will be returned
     * @param dbType the database where the value was stored and further keys should be searched for
     * @return an empty map when the value does not reference other trie nodes or the given limit is
     *     invalid, or a map containing all the referenced nodes reached while keeping within the
     *     limit on the result size
     */
    Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(
            byte[] value, int limit, DatabaseType dbType);
}
