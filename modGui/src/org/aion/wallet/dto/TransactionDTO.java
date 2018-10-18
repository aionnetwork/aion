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
package org.aion.wallet.dto;

import org.aion.base.util.TypeConverter;

import java.math.BigInteger;
import java.util.Objects;

public class TransactionDTO implements Comparable<TransactionDTO> {
    private final String from;
    private final String to;
    private final String hash;
    private final BigInteger value;
    private final long nrg;
    private final long nrgPrice;
    private final long timeStamp;
    private final Long blockNumber;
    private final BigInteger nonce;
    private final int txIndex;

    public TransactionDTO(final String from, final String to, final String hash, final BigInteger value, final long nrg, final long nrgPrice, final long timeStamp, final long blockNumber, BigInteger nonce, final int txIndex) {
        this.from = TypeConverter.toJsonHex(from);
        this.to = TypeConverter.toJsonHex(to);
        this.hash = hash;
        this.value = value;
        this.nrg = nrg;
        this.nrgPrice = nrgPrice;
        this.timeStamp = timeStamp;
        this.blockNumber = blockNumber;
        this.nonce = nonce;
        this.txIndex = txIndex;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getHash() {
        return hash;
    }

    public BigInteger getValue() {
        return value;
    }

    public Long getNrg() {
        return nrg;
    }

    public Long getNrgPrice() {
        return nrgPrice;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public BigInteger getNonce() {
        return nonce;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TransactionDTO that = (TransactionDTO) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(to, that.to) &&
                Objects.equals(hash, that.hash) &&
                Objects.equals(value, that.value) &&
                Objects.equals(blockNumber, that.blockNumber) &&
                Objects.equals(nonce, that.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, hash, value, blockNumber, nonce);
    }

    @Override
    public int compareTo(final TransactionDTO that) {
        final int comparison;
        if (that == null) {
            comparison = 1;
        } else {
            final int blockCompare = that.blockNumber.compareTo(this.blockNumber);
            if (blockCompare == 0) {
                if (this.from.equals(that.from)) {
                    comparison = that.nonce.compareTo(this.nonce);
                } else {
                    comparison = Integer.compare(that.txIndex, this.txIndex);
                }
            } else {
                comparison = blockCompare;
            }
        }
        return comparison;
    }
}
