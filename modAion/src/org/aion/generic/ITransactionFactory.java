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
 *     Centrys Inc. <https://centrys.io>
 */

package org.aion.generic;

import org.aion.base.type.Address;
import org.aion.base.type.ITransaction;

import java.math.BigInteger;

public interface ITransactionFactory<TX extends ITransaction> {
    TX createTransaction(BigInteger nonce, Address to, BigInteger value, byte[] data);

    TX createTransaction(byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice);

    TX createTransaction(byte[] encodedTransaction);

    TX createTransaction(byte[] nonce, Address from, Address to, byte[] value, byte[] data, long nrg, long nrgPrice);
}
