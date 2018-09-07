package org.libsodium.jni.crypto;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.encoders.Hex;

public class SecretStreamTest {
    public static final String message1 = "Arbitrary data to encrypt";
    public static final String message2 = "split into";
    public static final String message3 = "three messages";
    public static final String cipherText1 = "e9d5b0eceb20feda8af55547b0d3d8aa32b349cfe1c9e97d4220243691aea56a6281fced9c37bc57cc64";
    public static final String cipherText2 = "24d51ef9c941c8cd7af13bc7260cdb0b4074a5d8da006151610a0a";
    public static final String cipherText3 = "7d6180002f6d0248512fcb048e0d64baf536c994255af0cf7ae493e88f1d6d";
    public static final String testKey = "6ad964e0e2cb155e662521242ef50023b1b03cf4736c7f1e2544aa5aa90c21ad";
    public static final String testHeader = "05a5f60c257d6e004f656d72d764e150da0cc6239e63c444";
    public static final String testState = "e0644ca907a613c71fe0399b17984d8e2868f4fcda4f223adcb662cd6adc8d9001000000da0cc6239e63c4440000000000000000";

    @Test
    public void testEncryption() {
        Hex hex = new Hex();

        byte[] state = hex.decode(testState);
        byte[] ad = new byte[1];
        int[] clen = new int[1];

        byte[] c1 = new byte[25 + 17];
        byte[] c2 = new byte[10 + 17];
        byte[] c3 = new byte[14 + 17];

        Sodium.crypto_secretstream_xchacha20poly1305_push(state, c1, clen, message1.getBytes(), 25, ad, 0, (short)0);
        assertEquals(hex.encode(c1), cipherText1);

        Sodium.crypto_secretstream_xchacha20poly1305_push(state, c2, clen, message2.getBytes(), 10, ad, 0, (short)0);
        assertEquals(hex.encode(c2), cipherText2);

        Sodium.crypto_secretstream_xchacha20poly1305_push(state, c3, clen, message3.getBytes(), 14, ad, 0, (short)3);
        assertEquals(hex.encode(c3), cipherText3);
    }

    @Test
    public void testDecryption() {
        Hex hex = new Hex();

        byte[] state = new byte[52];
        byte[] key = hex.decode(testKey);
        byte[] header = hex.decode(testHeader);
        byte[] ad = new byte[1];
        int[] mlen = new int[1];
        byte[] tag = new byte[1];

        byte[] c1 = hex.decode(cipherText1);
        byte[] c2 = hex.decode(cipherText2);
        byte[] c3 = hex.decode(cipherText3);

        byte[] m1 = new byte[25];
        byte[] m2 = new byte[10];
        byte[] m3 = new byte[14];

        if (Sodium.crypto_secretstream_xchacha20poly1305_init_pull(state, header, key) != 0) {
            fail("Should return 0. Header is invalid.");
        }

        if (Sodium.crypto_secretstream_xchacha20poly1305_pull(state, m1, mlen, tag, c1, 25 + 17, ad, 0) != 0) {
            fail("Should return 0. Corrupted cipherText.");
        }
        assertArrayEquals(m1, message1.getBytes());

        if (Sodium.crypto_secretstream_xchacha20poly1305_pull(state, m2, mlen, tag, c2, 10 + 17, ad, 0) != 0) {
            fail("Should return 0. Corrupted cipherText.");
        }
        assertArrayEquals(m2, message2.getBytes());

        if (Sodium.crypto_secretstream_xchacha20poly1305_pull(state, m3, mlen, tag, c3, 14 + 17, ad, 0) != 0) {
            fail("Should return 0. Corrupted cipherText.");
        }
        assertArrayEquals(m3, message3.getBytes());
    }
}