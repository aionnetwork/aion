package org.aion.mcf.vm.types;

import java.util.Arrays;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.api.interfaces.IBloomFilter;

/** Utility class for creating/operating bloom. */
public class Bloom implements IBloomFilter {

    public byte[] data = new byte[IBloomFilter.SIZE];

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
        byte[] data = new byte[IBloomFilter.SIZE];
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
