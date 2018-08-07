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
package org.aion.zero.blockchain;

import java.math.BigInteger;

import org.aion.base.type.Address;
import org.aion.base.type.Hash256;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.core.AbstractTxInfo;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.types.AbstractBlockHeader;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * proof of work chain interface.
 *
 * @param <BLK>
 * @param <BH>
 */
@SuppressWarnings("rawtypes")
public interface IPowChain<
        BLK extends IBlock,
        BH extends AbstractBlockHeader,
        TX extends ITransaction,
        TR extends AbstractTxReceipt,
        INFO extends AbstractTxInfo> extends IBlockchain<BLK, BH, TX, TR, INFO> {

    BigInteger getTotalDifficulty();

    void setTotalDifficulty(BigInteger totalDifficulty);

    BigInteger getTotalDifficultyByHash(Hash256 hash);

    Address getMinerCoinbase();
}