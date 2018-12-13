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
package org.aion.mcf.vm.types;

import java.util.Arrays;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.api.interfaces.IBloomFilter;

/** Utility class for creating/operating bloom. */
public class Bloom implements IBloomFilter {

    public byte[] data = new byte[256];

    public Bloom() {}

    public Bloom(byte[] data) {
        this.data = data;
    }

    public static Bloom create(byte[] toBloom) {

        // value range: [0, 2^12-1=4096]
        int mov1 = (((toBloom[0] & 0xff) & 7) << 8) + ((toBloom[1]) & 0xff);
        int mov2 = (((toBloom[2] & 0xff) & 7) << 8) + ((toBloom[3]) & 0xff);
        int mov3 = (((toBloom[4] & 0xff) & 7) << 8) + ((toBloom[5]) & 0xff);

        // # bits: 8 * 256 = 2048
        byte[] data = new byte[256];
        Bloom bloom = new Bloom(data);

        ByteUtil.setBit(data, mov1, 1);
        ByteUtil.setBit(data, mov2, 1);
        ByteUtil.setBit(data, mov3, 1);

        return bloom;
    }

    @Override
    public void or(IBloomFilter bloom) {
        for (int i = 0; i < data.length; ++i) {
            data[i] |= bloom.getBloomFilterBytes()[i];
        }
    }

    @Override
    public void and(IBloomFilter bloom) {
        for (int i = 0; i < data.length; ++i) {
            data[i] &= bloom.getBloomFilterBytes()[i];
        }
    }

    @Override
    public boolean matches(IBloomFilter topicBloom) {
        Bloom copy = copy();
        copy.or(topicBloom);
        return this.equals(copy);
    }

    /**
     * Checks if this bloom contains another bloom, this can be used to construct queries from
     * creating blooms
     *
     * @param topicBloom another bloom already set with bloomBits
     * @return {@code true} if our bloom contains other bloom, {@code false} otherwise
     */
    @Override
    public boolean contains(IBloomFilter topicBloom) {
        Bloom copy = copy();
        copy.and(topicBloom);
        return topicBloom.equals(copy);
    }

    @Override
    public byte[] getBloomFilterBytes() {
        return data;
    }

    public Bloom copy() {
        return new Bloom(Arrays.copyOf(getBloomFilterBytes(), getBloomFilterBytes().length));
    }

    @Override
    public String toString() {
        return Hex.toHexString(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Bloom bloom = (Bloom) o;

        return Arrays.equals(data, bloom.data);
    }

    @Override
    public int hashCode() {
        return data != null ? Arrays.hashCode(data) : 0;
    }
}
