package org.libsodium.jni.publickey;

import com.google.common.io.Files;
import org.junit.Assert;
import org.junit.Test;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by joshjdevl on 2/5/16.
 */
public class SealedBoxTest {

    @Test
    public void testSealedBox() {
        Sodium sodium= NaCl.sodium();
        int ret=0;

        long publickeylen=Sodium.crypto_box_publickeybytes();
        long privatekeylen=Sodium.crypto_box_secretkeybytes();
        final byte[] public_key=new byte[(int)publickeylen];
        final byte[] private_key=new byte[(int)privatekeylen];

        Sodium.crypto_box_keypair(public_key,private_key);

        String message="testmessage";

        byte[] ciphertext=new byte[Sodium.crypto_box_sealbytes()+message.length()];
        Sodium.crypto_box_seal(ciphertext,message.getBytes(),message.length(),public_key);

        byte[] plaintext=new byte[message.length()];
        ret=Sodium.crypto_box_seal_open(plaintext,ciphertext,ciphertext.length,public_key,private_key);
        Assert.assertEquals(0,ret);
    }

    @Test
    public void testSealedBoxFile() throws IOException, URISyntaxException {
        Sodium sodium= NaCl.sodium();
        int ret=0;

        long publickeylen=Sodium.crypto_box_publickeybytes();
        long privatekeylen=Sodium.crypto_box_secretkeybytes();
        final byte[] public_key=new byte[(int)publickeylen];
        final byte[] private_key=new byte[(int)privatekeylen];

        Sodium.crypto_box_keypair(public_key,private_key);

        File public_key_file=File.createTempFile("SealedBoxTest","box_public.key",TemporaryFile.temporaryFileDirectory());
        public_key_file.deleteOnExit();
        Files.write(public_key,public_key_file);

        File private_key_file=File.createTempFile("SealedBoxTest","box_private.key",TemporaryFile.temporaryFileDirectory());
        private_key_file.deleteOnExit();
        Files.write(private_key,private_key_file);

        byte[] public_key_fromfile = Files.toByteArray(public_key_file);
        byte[] private_key_fromfile = Files.toByteArray(private_key_file);

        String message="testmessage";

        byte[] ciphertext=new byte[Sodium.crypto_box_sealbytes()+message.length()];
        Sodium.crypto_box_seal(ciphertext,message.getBytes(),message.length(),public_key_fromfile);

        byte[] plaintext=new byte[message.length()];
        ret=Sodium.crypto_box_seal_open(plaintext,ciphertext,ciphertext.length,public_key_fromfile,private_key_fromfile);
        Assert.assertEquals(0,ret);
    }
}
