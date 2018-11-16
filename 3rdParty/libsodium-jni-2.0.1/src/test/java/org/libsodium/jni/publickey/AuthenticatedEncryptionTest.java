package org.libsodium.jni.publickey;

import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by joshjdevl on 1/24/16.
 */
public class AuthenticatedEncryptionTest {

    @Test
    public void testAuthenticatedEncryption() {
        Sodium sodium= NaCl.sodium();
        long alice_publickeylen=Sodium.crypto_box_publickeybytes();
        long alice_privatekeylen=Sodium.crypto_box_secretkeybytes();
        byte[] alice_public_key=new byte[(int)alice_publickeylen];
        byte[] alice_private_key=new byte[(int)alice_privatekeylen];
        System.out.println("Generating keypair");
        int ret=Sodium.crypto_box_keypair(alice_public_key,alice_private_key);
        Assert.assertEquals(0,ret);
        System.out.println(ret);
        System.out.println("Generated keyapir");

        long bob_publickeylen=Sodium.crypto_box_publickeybytes();
        long bob_privatekeylen=Sodium.crypto_box_secretkeybytes();
        byte[] bob_public_key=new byte[(int)bob_publickeylen];
        byte[] bob_private_key=new byte[(int)bob_privatekeylen];
        System.out.println("Generating keypair");
        ret=Sodium.crypto_box_keypair(bob_public_key,bob_private_key);
        Assert.assertEquals(0,ret);
        System.out.println(ret);
        System.out.println("Generated keyapir");


        byte[] message="test".getBytes();
        long noncelen=Sodium.crypto_box_noncebytes();
        byte[] nonce=new byte[(int)noncelen];
        long ciphertextlen=Sodium.crypto_box_macbytes()+message.length;
        byte[] ciphertext=new byte[(int)ciphertextlen];

        Sodium.randombytes_buf(nonce,(int)noncelen);
        ret=Sodium.crypto_box_easy(ciphertext,message,message.length,nonce,bob_public_key,alice_private_key);
        Assert.assertEquals(0,ret);

        byte[] decrypted=new byte[message.length];
        ret=Sodium.crypto_box_open_easy(decrypted, ciphertext, (int)ciphertextlen, nonce,
                alice_public_key, bob_private_key);
        Assert.assertEquals(0,ret);
        System.out.println("Recovered message="+new String(decrypted));

    }
}
