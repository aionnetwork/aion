package org.libsodium.jni.crypto;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.NaCl;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PwhashTest {

    @Test
    public void testGenericHashInit() {
        Sodium sodium= NaCl.sodium();
        String Password = "hunter2";
        byte[] key = new byte[Sodium.crypto_box_seedbytes()]; 
        byte[] passwd = Password.getBytes();
        byte[] salt = new byte[]{ 88, (byte)240, (byte)185, 66, (byte)195, 101, (byte)160, (byte)138, (byte)137, 78, 1, 2, 3, 4, 5, 6};

        Sodium.crypto_pwhash(
            key,
            key.length,
            passwd,
            passwd.length,
            salt,
            Sodium.crypto_pwhash_opslimit_interactive(),
            Sodium.crypto_pwhash_memlimit_interactive(),
            Sodium.crypto_pwhash_alg_default()
        );
        //assertEquals(key[0], (byte)49);
    }
}
