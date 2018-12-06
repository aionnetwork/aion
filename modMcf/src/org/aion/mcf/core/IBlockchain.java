/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.core;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.type.AionAddress;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Blockchain interface.
 *
 * @param <BLK>
 * @param <BH>
 * @param <TX>
 * @param <TR>
 * @param <INFO>
 */
@SuppressWarnings("rawtypes")
public interface IBlockchain<
                BLK extends IBlock,
                BH extends AbstractBlockHeader,
                TX extends ITransaction,
                TR extends AbstractTxReceipt,
                INFO extends AbstractTxInfo>
        extends IPowChain<BLK, BH> {

    long getSize();

    AbstractBlockSummary add(BLK block);

    AbstractBlockSummary add(BLK block, boolean rebuild);

    ImportResult tryToConnect(BLK block);

    void storeBlock(BLK block, List<TR> receipts);

    /**
     * Attempts to store the given block in the pending block store, saving it to be imported later
     * when the chain has reached the required height.
     *
     * @param block a future block that cannot be imported due to height
     * @return {@code true} when the block was imported, {@code false} if the block is already
     *     stored and saving it was not necessary.
     * @apiNote Functionality used to store blocks coming from <b>status requests</b>.
     */
    boolean storePendingStatusBlock(BLK block);

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
    int storePendingBlockRange(List<BLK> blocks);

    /**
     * Retrieves ranges of blocks from the pending block store for a specific blockchain height.
     *
     * @param level the blockchain height of interest
     * @return a map containing all the block ranges that are stored in the pending block store at
     *     the given height. The map may be empty if there is no data stored for the given height.
     *     It may also contain several entries if there are multiple ranges starting at the given
     *     level due to the storage of different chains.
     */
    Map<ByteArrayWrapper, List<BLK>> loadPendingBlocksAtLevel(long level);

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
            long level, List<ByteArrayWrapper> ranges, Map<ByteArrayWrapper, List<BLK>> blocks);

    void setBestBlock(BLK block);

    boolean hasParentOnTheChain(BLK block);

    void close();

    void setTotalDifficulty(BigInteger totalDifficulty);

    byte[] getBestBlockHash();

    List<byte[]> getListOfHashesEndWith(byte[] hash, int qty);

    List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty);

    INFO getTransactionInfo(byte[] hash);

    void setExitOn(long exitOn);

    AionAddress getMinerCoinbase();

    boolean isBlockExist(byte[] hash);

    List<BH> getListOfHeadersStartFrom(long number, int limit);

    // /** Returns the list of headers for the main chain.
    //  *  Returns emptyList() for side chain blocks.
    //  */
    // List<BH> getListOfHeadersStartFrom(
    //         BlockIdentifier identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes);
}
