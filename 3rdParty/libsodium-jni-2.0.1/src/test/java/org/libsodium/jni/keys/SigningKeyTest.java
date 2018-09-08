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

package org.libsodium.jni.keys;

import org.junit.Test;

import java.util.Arrays;

import static org.libsodium.jni.encoders.Encoder.HEX;
import static org.libsodium.jni.fixture.TestVectors.SIGN_MESSAGE;
import static org.libsodium.jni.fixture.TestVectors.SIGN_PRIVATE;
import static org.libsodium.jni.fixture.TestVectors.SIGN_SIGNATURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SigningKeyTest {

    @Test
    public void testGenerateSigninKey() throws Exception {
        try {
            new SigningKey();
        } catch (Exception e) {
            fail("Should return a valid key size");
        }
    }

    @Test
    public void testAcceptsRawValidKey() throws Exception {
        try {
            byte[] rawKey = HEX.decode(SIGN_PRIVATE);
            new SigningKey(rawKey);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should return a valid key size");
        }
    }

    @Test
    public void testAcceptsHexValidKey() throws Exception {
        try {
            new SigningKey(SIGN_PRIVATE, HEX);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should return a valid key size");
        }
    }

    @Test
    public void testCreateHexValidKey() throws Exception {
        try {
            new SigningKey(SIGN_PRIVATE, HEX).toString();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should return a valid key size");
        }
    }

    @Test
    public void testCreateByteValidKey() throws Exception {
        try {
            new SigningKey(SIGN_PRIVATE, HEX).toBytes();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should return a valid key size");
        }
    }

    @Test(expected = RuntimeException.class)
    public void testRejectNullKey() throws Exception {
        byte[] key = null;
        new SigningKey(key);
        fail("Should reject null keys");
    }

    @Test(expected = RuntimeException.class)
    public void testRejectShortKey() throws Exception {
        byte[] key = "short".getBytes();
        new SigningKey(key);
        fail("Should reject short keys");
    }

    @Test
    public void testSignMessageAsBytes() throws Exception {
        byte[] rawKey = HEX.decode(SIGN_PRIVATE);
        byte[] signatureRaw = HEX.decode(SIGN_SIGNATURE);
        SigningKey key = new SigningKey(rawKey);
        byte[] signedMessage = key.sign(HEX.decode(SIGN_MESSAGE));
        assertTrue("Message sign has failed", Arrays.equals(signatureRaw, signedMessage));
    }

    @Test
    public void testSignMessageAsHex() throws Exception {
        SigningKey key = new SigningKey(SIGN_PRIVATE, HEX);
        String signature = key.sign(SIGN_MESSAGE, HEX);
        assertEquals("Message sign has failed", SIGN_SIGNATURE, signature);
    }

    @Test
    public void testSerializesToHex() throws Exception {
        try {
            SigningKey key = new SigningKey(SIGN_PRIVATE, HEX);
            assertEquals("Correct sign key expected", SIGN_PRIVATE, key.toString());
        } catch (Exception e) {
            fail("Should return a valid key size");
        }
    }

    @Test
    public void testSerializesToBytes() throws Exception {
        try {
            byte[] rawKey = HEX.decode(SIGN_PRIVATE);
            SigningKey key = new SigningKey(SIGN_PRIVATE, HEX);
            assertTrue("Correct sign key expected", Arrays.equals(rawKey,
                    key.toBytes()));
        } catch (Exception e) {
            fail("Should return a valid key size");
        }
    }
}
