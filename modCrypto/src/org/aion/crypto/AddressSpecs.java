package org.aion.crypto;

import org.aion.base.util.ByteUtil;

import java.nio.ByteBuffer;

/**
 * A set of static functions to define the creation
 * of addresses
 */
public class AddressSpecs {

    public static final byte A0_IDENTIFIER = ByteUtil.hexStringToBytes("0xA0")[0];

    private AddressSpecs() {}

    /**
     * Returns an address of with identifier A0, given the public
     * key of the account (this is currently our only account type)
     */
    public static byte[] computeA0Address(byte[] publicKey)  {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(A0_IDENTIFIER);
        // [1:]
        buf.put(HashUtil.h256(publicKey), 1, 31);
        return buf.array();
    }
}
