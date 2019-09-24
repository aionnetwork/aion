package org.aion.avm.stub;

import java.math.BigInteger;
import org.aion.types.AionAddress;

/**
 * A decoder used to decode objects that were encoded using the {@link IStreamingEncoder}.
 *
 * Any implementation of this class should have a constructor that accepts an encoding as a byte
 * array. The methods will be run against that encoding.
 */
public interface IDecoder {

    public int decodeOneInteger();

    public BigInteger decodeOneBigInteger();

    public String decodeOneString();

    public AionAddress decodeOneAddress();
}
