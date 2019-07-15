package org.aion.precompiled.type;

/**
 * A data word is a byte array that is aligned to a specific number of bytes, where that number is
 * dependent upon the implementation.
 */
public interface IPrecompiledDataWord {

    /**
     * Returns a copy of the underlying data.
     *
     * @return the underlying data.
     */
    byte[] copyOfData();
}
