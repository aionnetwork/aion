package org.aion.crypto;

import org.aion.base.util.ByteUtil;

import java.nio.ByteBuffer;
import java.util.BitSet;

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

    public static String checksummedAddress(String address) {
        assert address != null;
        address = address.replace("0x", "");
        assert address.length() == 64;
        byte[] h = HashUtil.h256(ByteUtil.hexStringToBytes(address));
        if (h == null) return null; //address is invalid
        System.out.println(ByteUtil.toHexString(h));
        BitSet b = BitSet.valueOf(h);
        char[] caddr = address.toCharArray();
        for (int i = 0; i < 64; i++) {
            if (Character.isDigit(caddr[i]))
                continue;

            if (Character.isAlphabetic(caddr[i])) {
                caddr[i] = b.get(i) ? Character.toUpperCase(caddr[i]) : Character.toLowerCase(caddr[i]);
                continue;
            }
        }
        return String.valueOf(caddr);
    }
}
