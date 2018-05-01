/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.core;

import org.aion.base.type.Address;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.mcf.types.BlockIdentifier;

import java.math.BigInteger;
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
public interface IBlockchain<BLK extends IBlock, BH extends AbstractBlockHeader, TX extends ITransaction, TR extends AbstractTxReceipt, INFO extends AbstractTxInfo>
        extends IPowChain<BLK, BH> {

    long getSize();

    AbstractBlockSummary add(BLK block);

    AbstractBlockSummary add(BLK block, boolean rebuild);

    ImportResult tryToConnect(BLK block);

    void storeBlock(BLK block, List<TR> receipts);

    void setBestBlock(BLK block);

    boolean hasParentOnTheChain(BLK block);

    void close();

    void setTotalDifficulty(BigInteger totalDifficulty);

    byte[] getBestBlockHash();

    List<byte[]> getListOfHashesStartFrom(byte[] hash, int qty);

    List<byte[]> getListOfHashesStartFromBlock(long blockNumber, int qty);

    INFO getTransactionInfo(byte[] hash);

    void setExitOn(long exitOn);

    Address getMinerCoinbase();

    boolean isBlockExist(byte[] hash);

    List<BH> getListOfHeadersStartFrom(BlockIdentifier identifier, int skip, int limit, boolean reverse);

    List<byte[]> getListOfBodiesByHashes(List<byte[]> hashes);

}
