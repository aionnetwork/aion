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

import java.util.List;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IChainCfg;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;

import java.util.List;

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
                INFO extends AbstractTxInfo> {

    long getSize();

    BLK createNewBlock(BLK parent, List<TX> transactions, boolean waitUntilBlockTime);

    AbstractBlockSummary add(BLK block);

    AbstractBlockSummary add(BLK block, boolean rebuild);

    ImportResult tryToConnect(BLK block);

    void storeBlock(BLK block, List<TR> receipts);

    void setBestBlock(BLK block);

    boolean hasParentOnTheChain(BLK block);

    void close();

    byte[] getBestBlockHash();

    List<byte[]> getListOfHashesEndWith(byte[] hash, int qty);

    List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty);

    INFO getTransactionInfo(byte[] hash);

    void setExitOn(long exitOn);

    boolean isBlockExist(byte[] hash);

    List<BH> getListOfHeadersStartFrom(long number, int limit);

    // /** Returns the list of headers for the main chain.
    //  *  Returns emptyList() for side chain blocks.
    //  */
    // List<BH> getListOfHeadersStartFrom(
    //         BlockIdentifier identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes);

    BLK getBlockByNumber(long number);

    BLK getBlockByHash(byte[] hash);

    IBlockStoreBase<?, ?> getBlockStore();

    BLK getBestBlock();

    void flush();

    IChainCfg<?, ?> getChainConfiguration();
}
