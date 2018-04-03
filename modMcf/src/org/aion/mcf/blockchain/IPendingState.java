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
package org.aion.mcf.blockchain;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;

public interface IPendingState<TX extends ITransaction> {

    List<TX> addPendingTransactions(List<TX> transactions);

    List<TX> addPendingTransaction(TX tx);

    IRepositoryCache<?, ?, ?> getRepository();

    List<TX> getPendingTransactions();

    BigInteger bestPendingStateNonce(Address addr);

    String getVersion();
}
