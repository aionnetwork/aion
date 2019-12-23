package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.db.Repository;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.sync.DatabaseType;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;

/** aion blockchain interface. */
public interface IAionBlockchain extends UnityChain {

    AionTxInfo getTransactionInfo(byte[] hash);

    AionBlock createNewMiningBlock(Block parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);

    BlockContext createNewMiningBlockContext(
            Block parent, List<AionTransaction> transactions, boolean waitUntilBlockTime);
    
    Block getBestBlock();

    Block getBlockByNumber(long num);

    /**
     * Returns a range of main chain blocks.
     *
     * @param first the height of the first block in the requested range; this block must exist in
     *     the blockchain and be above the genesis to return a non-null output
     * @param last the height of the last block in the requested range; when requesting blocks in
     *     ascending order the last element will be substituted with the best block if its height is
     *     above the best known block
     * @return a list containing consecutive main chain blocks with heights ranging according to the
     *     given parameters; or {@code null} in case of errors or illegal request
     * @apiNote The blocks must be added to the list in the order that they are requested. If {@code
     *     first > last} the blocks are returned in descending order of their height, otherwise when
     *     {@code first < last} the blocks are returned in ascending order of their height.
     */
    List<Block> getBlocksByRange(long first, long last);

    /**
     * Recovery functionality for rebuilding the world state.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverWorldState(Repository repository, Block block);

    /**
     * Recovery functionality for recreating the block info in the index database.
     *
     * @return {@code true} if the recovery was successful, {@code false} otherwise
     */
    boolean recoverIndexEntry(Repository repository, Block block);

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

    long getSize();

    /**
     * Attempts to store the given range of blocks in the pending block store, saving them to be
     * imported later when the chain has reached the required height or has imported the needed
     * parent block. The blocks from the range that are already stored with be skipped.
     *
     * @param blocks a range of blocks that cannot be imported due to height or lack of parent block
     * @return an integer value (ranging from zero to the number of given blocks) representing the
     *     number of blocks that were stored from the given input.
     * @apiNote Functionality used to store blocks coming from <b>range import requests</b>.
     */
    int storePendingBlockRange(List<Block> blocks);

    /**
     * Retrieves ranges of blocks from the pending block store for a specific blockchain height.
     *
     * @param level the blockchain height of interest
     * @return a map containing all the block ranges that are stored in the pending block store at
     *     the given height. The map may be empty if there is no data stored for the given height.
     *     It may also contain several entries if there are multiple ranges starting at the given
     *     level due to the storage of different chains.
     */
    Map<ByteArrayWrapper, List<Block>> loadPendingBlocksAtLevel(long level);

    /**
     * Deletes the given blocks from the pending block storage.
     *
     * @param level the block height of the range starting point
     * @param ranges the identifiers for the ranges to be deleted
     * @param blocks the range identifier to blocks mappings to me deleted (used to ensure that if
     *     the ranges have been expanded, only the relevant blocks get deleted)
     */
    void dropImported(
        long level, List<ByteArrayWrapper> ranges, Map<ByteArrayWrapper, List<Block>> blocks);

    void setBestBlock(Block block);

    boolean hasParentOnTheChain(Block block);

    void close();

    void setTotalDifficulty(BigInteger totalDifficulty);

    byte[] getBestBlockHash();

    List<byte[]> getListOfHashesEndWith(byte[] hash, int qty);

    List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty);

    void setExitOn(long exitOn);

    AionAddress getMinerCoinbase();

    boolean isBlockStored(byte[] hash, long number);

    List<BlockHeader> getListOfHeadersStartFrom(long number, int limit);

    // /** Returns the list of headers for the main chain.
    //  *  Returns emptyList() for side chain blocks.
    //  */
    // List<BH> getListOfHeadersStartFrom(
    //         BlockIdentifierImpl identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes);

    /**
     * Checks whether a hash is indexed as main chain or side chain.
     *
     * @param hash the hash for which we check its chain status
     * @param level the height at which the block should be indexed
     * @return {@code true} if the block is indexed as a main chain block, {@code false} if the
     *     block is not indexed or is a side chain block
     */
    boolean isMainChain(byte[] hash, long level);

    /**
     * Checks if a hash is indexed as main chain or side chain
     *
     * @param hash the hash for which we check its chain status
     *
     * @return {@code true} if the block is indexed as a main chain block, {@code false} if the
     *     block is not indexed or is a side chain block
     */
    public boolean isMainChain(byte[] hash);
}
