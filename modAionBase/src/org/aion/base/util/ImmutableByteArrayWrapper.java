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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */

package org.aion.base.util;

import java.util.Arrays;

/**
 * Immutable byte array wrapper used when storing keys inside HashMap, this way we guarantee that
 * keys inside HashMap never change
 *
 * @author yao
 */
public class ImmutableByteArrayWrapper implements Comparable<ImmutableByteArrayWrapper> {
    protected byte[] data;
    protected int hashCode;

    /** For us to be able to create MutableByteArrayObserver */
    protected ImmutableByteArrayWrapper() {
        data = null;
        hashCode = 0;
    }

    public ImmutableByteArrayWrapper(byte[] data) {
        if (data == null) throw new NullPointerException("data cannot be null");
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
        this.hashCode = Arrays.hashCode(this.data);
    }

    public ByteArrayWrapper toByteArrayWrapper() {
        byte[] d = new byte[data.length];
        System.arraycopy(this.data, 0, d, 0, this.data.length);
        return new ByteArrayWrapper(d);
    }

    public byte[] getData() {
        byte[] d = new byte[data.length];
        System.arraycopy(this.data, 0, d, 0, this.data.length);
        return d;
    }

    /**
     * Utility constructor, for us to easily convert ByteArrayWrapper over to immutable form
     *
     * @param other
     */
    public ImmutableByteArrayWrapper(ByteArrayWrapper other) {
        this(other.getData());
    }

    /**
     * Copy constructor
     *
     * @param other
     */
    public ImmutableByteArrayWrapper(ImmutableByteArrayWrapper other) {
        this(other.data);
    }

    /** Allow comparisons between both ByteArrayWrapper and ImmutableByteArrayWrapper */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper) && !(other instanceof ImmutableByteArrayWrapper)) {
            return false;
        }

        byte[] otherData = null;
        if (other instanceof ByteArrayWrapper) otherData = ((ByteArrayWrapper) other).getData();

        if (other instanceof ImmutableByteArrayWrapper)
            otherData = ((ImmutableByteArrayWrapper) other).data;

        // probably impossible, but be safe
        if (otherData == null) return false;

        return FastByteComparisons.compareTo(data, 0, data.length, otherData, 0, otherData.length)
                == 0;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return ByteUtil.toHexString(this.data);
    }

    /** TODO: what happens when one is null and the other is not? */
    @Override
    public int compareTo(ImmutableByteArrayWrapper o) {
        return FastByteComparisons.compareTo(data, 0, data.length, o.data, 0, o.data.length);
    }
}
