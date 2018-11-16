/**
 * Copyright 2013 Bruno Oliveira, and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.libsodium.jni.crypto;

import org.libsodium.jni.keys.PrivateKey;
import org.libsodium.jni.keys.PublicKey;
import org.junit.Test;
import org.libsodium.jni.fixture.TestVectors;

import java.util.Arrays;

import static org.libsodium.jni.encoders.Encoder.HEX;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class BoxTest {

    @Test
    public void testAcceptStrings() throws Exception {
        try {
            new Box(TestVectors.ALICE_PUBLIC_KEY, TestVectors.BOB_PRIVATE_KEY, HEX);
        } catch (Exception e) {
            fail("Box should accept strings");
        }
    }

    @Test
    public void testAcceptKeyPairs() throws Exception {
        try {
            new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        } catch (Exception e) {
            fail("Box should accept key pairs");
        }
    }

    @Test(expected = RuntimeException.class)
    public void testNullPublicKey() throws Exception {
        String key = null;
        new Box(new PublicKey(key), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        fail("Should raise an exception");
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidPublicKey() throws Exception {
        String key = "hello";
        new Box(new PublicKey(key), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        fail("Should raise an exception");
    }

    @Test(expected = RuntimeException.class)
    public void testNullSecretKey() throws Exception {
        String key = null;
        new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(key));
        fail("Should raise an exception");
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidSecretKey() throws Exception {
        String key = "hello";
        new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(key));
        fail("Should raise an exception");
    }

    @Test
    public void testEncryptRawBytes() throws Exception {
        Box box = new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        byte[] nonce = HEX.decode(TestVectors.BOX_NONCE);
        byte[] message = HEX.decode(TestVectors.BOX_MESSAGE);
        byte[] ciphertext = HEX.decode(TestVectors.BOX_CIPHERTEXT);

        byte[] result = box.encrypt(nonce, message);
        assertTrue("failed to generate ciphertext", Arrays.equals(result, ciphertext));
    }

    @Test
    public void testDecryptRawBytes() throws Exception {
        Box box = new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        byte[] nonce = HEX.decode(TestVectors.BOX_NONCE);
        byte[] expectedMessage = HEX.decode(TestVectors.BOX_MESSAGE);
        byte[] ciphertext = box.encrypt(nonce, expectedMessage);

        Box pandora = new Box(new PublicKey(TestVectors.BOB_PUBLIC_KEY), new PrivateKey(TestVectors.ALICE_PRIVATE_KEY));
        byte[] message = pandora.decrypt(nonce, ciphertext);
        assertTrue("failed to decrypt ciphertext", Arrays.equals(message, expectedMessage));
    }

    @Test
    public void testEncryptHexBytes() throws Exception {
        Box box = new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        byte[] ciphertext = HEX.decode(TestVectors.BOX_CIPHERTEXT);

        byte[] result = box.encrypt(TestVectors.BOX_NONCE, TestVectors.BOX_MESSAGE, HEX);
        assertTrue("failed to generate ciphertext", Arrays.equals(result, ciphertext));
    }

    @Test
    public void testDecryptHexBytes() throws Exception {
        Box box = new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        byte[] expectedMessage = HEX.decode(TestVectors.BOX_MESSAGE);
        byte[] ciphertext = box.encrypt(TestVectors.BOX_NONCE, TestVectors.BOX_MESSAGE, HEX);

        Box pandora = new Box(new PublicKey(TestVectors.BOB_PUBLIC_KEY), new PrivateKey(TestVectors.ALICE_PRIVATE_KEY));
        byte[] message = pandora.decrypt(TestVectors.BOX_NONCE, HEX.encode(ciphertext), HEX);
        assertTrue("failed to decrypt ciphertext", Arrays.equals(message, expectedMessage));
    }

    @Test(expected = RuntimeException.class)
    public void testDecryptCorruptedCipherText() throws Exception {
        Box box = new Box(new PublicKey(TestVectors.ALICE_PUBLIC_KEY), new PrivateKey(TestVectors.BOB_PRIVATE_KEY));
        byte[] nonce = HEX.decode(TestVectors.BOX_NONCE);
        byte[] message = HEX.decode(TestVectors.BOX_MESSAGE);
        byte[] ciphertext = box.encrypt(nonce, message);
        ciphertext[23] = ' ';

        Box pandora = new Box(new PublicKey(TestVectors.BOB_PUBLIC_KEY), new PrivateKey(TestVectors.ALICE_PRIVATE_KEY));
        pandora.decrypt(nonce, ciphertext);
        fail("Should raise an exception");
    }
}
