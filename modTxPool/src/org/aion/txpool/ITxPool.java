/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.txpool;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface ITxPool<TX extends ITransaction> {

    String PROP_TXN_TIMEOUT = "txn-timeout";
    String PROP_BLOCK_SIZE_LIMIT = "blk-size-limit";
    String PROP_BLOCK_NRG_LIMIT = "blk-nrg-limit";

    List<TX> add(List<TX> tx);

    boolean add(TX tx);

    List<TX> remove(List<TX> tx);

    int size();

    List<TX> snapshot(boolean unlimited);

    List<TX> getOutdatedList();

    long getOutDateTime();

    Map.Entry<BigInteger, BigInteger> bestNonceSet(Address addr);

    void updateBlkNrgLimit(long nrg);

    @SuppressWarnings("SameReturnValue")
    String getVersion();
}
