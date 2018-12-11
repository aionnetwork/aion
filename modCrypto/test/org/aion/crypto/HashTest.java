package org.aion.crypto;

import static org.junit.Assert.assertEquals;

import org.aion.base.util.Hex;
import org.junit.Test;

public class HashTest {

    @Test
    public void testSha256() {
        String expected = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

        String hash = Hex.toHexString(HashUtil.sha256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testKeccak256() {
        String expected = "9c22ff5f21f0b81b113e63f7db6da94fedef11b2119b4088b89664fb9a3cb658";

        String hash = Hex.toHexString(HashUtil.keccak256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.keccak256("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testBlake256() {
        String expected = "928b20366943e2afd11ebc0eae2e53a93bf177a4fcf35bcc64d503704e65e202";

        String hash = Hex.toHexString(HashUtil.blake256("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.blake256("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }

    @Test
    public void testBlake256Native() {
        String expected = "928b20366943e2afd11ebc0eae2e53a93bf177a4fcf35bcc64d503704e65e202";

        String hash = Hex.toHexString(HashUtil.blake256Native("test".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);

        hash = Hex.toHexString(HashUtil.blake256Native("te".getBytes(), "st".getBytes()));
        System.out.println(hash);
        assertEquals(expected, hash);
    }
}
