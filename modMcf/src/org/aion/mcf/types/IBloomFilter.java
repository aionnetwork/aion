package org.aion.mcf.types;

/**
 * A bloom filter.
 *
 * <p>All implementations of this filter must use a filter that consists of {@value SIZE} bytes, so
 * that {@code getBloomFilterBytes().length == SIZE}.
 */
public interface IBloomFilter {

    /** The number of bytes that the filter consists of. */
    int SIZE = 256;

    /**
     * Returns the byte array that is representative of this bloom filter.
     *
     * @return This filter as a byte array.
     */
    byte[] getBloomFilterBytes();

    /**
     * Performs a logical-or on each bit in this filter and the provided filter. The result of this
     * logical-or is the new state of this filter when this operation finishes.
     *
     * @param otherBloomFilter The other filter to perform a logical-or on.
     */
    void or(IBloomFilter otherBloomFilter);

    /**
     * Performs a logical-and on each bit in this filter and the provided filter. The result of this
     * logical-and is the new state of this filter when this operation finishes.
     *
     * @param otherBloomFilter The other filter to perform a logical-and on.
     */
    void and(IBloomFilter otherBloomFilter);

    // TODO: we need to get clear definitions on these two methods. They seem self-explanatory but
    // TODO: the current kernel implementation of these methods feels like it may be wrong or like
    // TODO: there may be additional subtleties involved.

    boolean matches(IBloomFilter otherBloomFilter);

    boolean contains(IBloomFilter otherBloomFilter);
}
