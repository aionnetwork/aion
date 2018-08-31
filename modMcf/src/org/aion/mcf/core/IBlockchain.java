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
import org.aion.base.type.Address;
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

    boolean storePendingBlock(BLK block);

    int storePendingBlockRange(List<BLK> blocks);

    Map<ByteArrayWrapper, List<BLK>> loadPendingBlocksAtLevel(long level);

    long nextBase(long current);

    void dropImported(long level, List<ByteArrayWrapper> queues, Map<ByteArrayWrapper, List<BLK>> blocks);

    void setBestBlock(BLK block);

    boolean hasParentOnTheChain(BLK block);

    void close();

    void setTotalDifficulty(BigInteger totalDifficulty);

    byte[] getBestBlockHash();

    List<byte[]> getListOfHashesEndWith(byte[] hash, int qty);

    List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty);

    INFO getTransactionInfo(byte[] hash);

    void setExitOn(long exitOn);

    Address getMinerCoinbase();

    boolean isBlockExist(byte[] hash);

    List<BH> getListOfHeadersStartFrom(long number, int limit);

    // /** Returns the list of headers for the main chain.
    //  *  Returns emptyList() for side chain blocks.
    //  */
    // List<BH> getListOfHeadersStartFrom(
    //         BlockIdentifier identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes);
}
