package org.aion.mcf.core;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/**
 * Blockchain interface.
 */
@SuppressWarnings("rawtypes")
public interface IBlockchain extends IPowChain {

    long getSize();

    ImportResult tryToConnect(Block block);

    /**
     * Attempts to store the given block in the pending block store, saving it to be imported later
     * when the chain has reached the required height.
     *
     * @param block a future block that cannot be imported due to height
     * @return {@code true} when the block was imported, {@code false} if the block is already
     *     stored and saving it was not necessary.
     * @apiNote Functionality used to store blocks coming from <b>status requests</b>.
     */
    boolean storePendingStatusBlock(Block block);

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
     * Returns a number greater or equal to the given {@code current} number representing the base
     * value for a subsequent LIGHTNING request.
     *
     * @param current the starting point value for the next base
     * @param knownStatus value retrieved from the last best block status update for the peer
     *     requesting a base value for a subsequent LIGHTNING request.
     * @return the next generated base value for the request.
     */
    long nextBase(long current, long knownStatus);

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
}
