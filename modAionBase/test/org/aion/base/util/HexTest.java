package org.aion.base.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class HexTest {

    private final int size = 7;

    private final String[] testHex = {
            null,                                                                   // 0 - Null
            "",                                                                     // 1 - Empty
            "eF",                                                                   // 2 - One Byte
            "aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44aA11bB22cC33dd44",     // 3 - Upper/Lower
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",     // 4 - Negative (-1)
            "0000000000000000000000000000000000000000000000000000000000000000",     // 5 - Zeroes
            "0000000000000000000000000000000000000000000000000000000000000001",     // 6 - Positive (+1)
    };

    /**
     * toHexString(byte[])
     * toHexString(byte[], offset, length)
     */
    @Test
    public void testConvert() {

    }

    /**
     * encode(byte[])
     * encode(byte[], offset, length)
     * encode(byte[], outStream)
     * encode(byte[], offset, length, outStream)
     */
    @Test
    public void testEncode() {

    }

    /**
     * decode(byte[])
     * decode(String)
     * decode(String, outStream)
     */
    @Test
    public void testDecode() {

    }

}