package org.aion.avm.stub;

import org.aion.types.AionAddress;

/**
 * A streaming encoder used to encode arguments to be sent to the avm.
 *
 * @implSpec This is a valid avm resource but will likely only be used for testing. Unfortunately, testing
 * resources that touch the avm directly require multi-versioned support and therefore get bundled
 * up into the source code. This isn't much harm, there's no damage you can do with this class.
 * It is really just that production code has no use for it (at the moment anyway).
 */
public interface IStreamingEncoder {

    /**
     * Encodes the specified long and returns this encoder.
     *
     * @param longToEncode The long to encode.
     * @return this encoder.
     */
    public IStreamingEncoder encodeOneLong(long longToEncode);

    /**
     * Encodes the specified string and returns this encoder.
     *
     * @param string The string to encode.
     * @return this encoder.
     */
    public IStreamingEncoder encodeOneString(String string);

    /**
     * Encodes the specified address and returns this encoder.
     *
     * @param address The address to encode.
     * @return this encoder.
     */
    public IStreamingEncoder encodeOneAddress(AionAddress address);

    /**
     * Encodes the specified byte array and returns this encoder.
     *
     * @param array The array to encode.
     * @return this encoder.
     */
    public IStreamingEncoder encodeOneByteArray(byte[] array);

    /**
     * Returns the encoding.
     *
     * @return the encoding.
     */
    public byte[] getEncoding();
}
