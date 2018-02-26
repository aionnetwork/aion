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

package org.aion.api.server.types;

import org.aion.base.util.TypeConverter;

/**
 * JSON representation of a transaction, with more information
 *
 * @author chris
 */
public class Tx {

    public String address;

    public String transactionHash;

    public String blockHash;

    public NumericalValue nonce;

    public NumericalValue transactionIndex;

    public String from;

    public String to;

    public NumericalValue timestamp;

    public NumericalValue value;

    public NumericalValue gas;

    public NumericalValue gasPrice;

    public String input;

    public NumericalValue blockNumber;

    public Tx(String address, String hash, String blockHash, NumericalValue nonce, String from, String to, NumericalValue timestamp,
            NumericalValue value, String input, NumericalValue blockNumber, NumericalValue gas, NumericalValue gasPrice,
            NumericalValue transactionIndex) {
        this.address = address;
        this.transactionHash = hash;
        this.blockHash =  blockHash;
        this.nonce = nonce;
        this.from = TypeConverter.toJsonHex(from);
        this.to = to == null ? null : TypeConverter.toJsonHex(to);
        this.timestamp = timestamp;
        this.value = value;
        this.input = input;
        this.blockNumber = blockNumber;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.transactionIndex = transactionIndex;
    }
}