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
package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.aion.base.type.Address;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * Internal pending state interface.
 *
 * @param <BLK>
 * @param <Tx>
 */

public interface IPendingStateInternal<BLK extends IBlock<?, ?>, Tx extends ITransaction> extends IPendingState<Tx> {



    void processBest(BLK block, List<? extends AbstractTxReceipt<Tx>> receipts);

    List<Tx> newTransactions(List<Tx> txSet);


    /**
     * get txpool version
     *
     * @return txpool version.
     * @jay
     */
    String getVersion();

    List<Tx> addToTxCache(Map<BigInteger, Tx> txmap, Address addr);

    List<Tx> getSeqCacheTx(Map<BigInteger, Tx> txmap, Address addr, BigInteger bn);

    Map<BigInteger,Tx> getCacheTx(Address from);
}
