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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.libsodium.jni.encoders.Encoder.HEX;
import static org.libsodium.jni.fixture.TestVectors.SIGN_MESSAGE;
import static org.libsodium.jni.fixture.TestVectors.SIGN_PRIVATE;
import static org.libsodium.jni.fixture.TestVectors.SIGN_PUBLIC;
import static org.libsodium.jni.fixture.TestVectors.SIGN_SIGNATURE;
import static org.junit.Assert.fail;

public class VerifyKeyTest {

    @Test
    public void testVerifyCorrectRawSignature() throws Exception {
        byte[] rawSignature = HEX.decode(SIGN_SIGNATURE);
        byte[] rawMessage = HEX.decode(SIGN_MESSAGE);
        byte[] rawPublicKey = HEX.decode(SIGN_PUBLIC);
        VerifyKey verifyKey = new VerifyKey(rawPublicKey);
        assertTrue(verifyKey.verify(rawMessage, rawSignature));
    }

    @Test
    public void testVerifyCorrectHexSignature() throws Exception {
        byte[] rawPublicKey = HEX.decode(SIGN_PUBLIC);
        VerifyKey verifyKey = new VerifyKey(rawPublicKey);
        verifyKey.verify(SIGN_MESSAGE, SIGN_SIGNATURE, HEX);
        assertTrue(verifyKey.verify(SIGN_MESSAGE, SIGN_SIGNATURE, HEX));
    }

    @Test
    public void testDetectBadSignature() throws Exception {
        try {
            String badSignature = SIGN_SIGNATURE.concat("0000");
            VerifyKey verifyKey = new VerifyKey(SIGN_PUBLIC, HEX);
            verifyKey.verify(SIGN_MESSAGE, badSignature, HEX);
            fail("Should an exception on bad signatures");
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSerializeToBytes() throws Exception {
        byte[] rawPublic = HEX.decode(SIGN_PUBLIC);
        VerifyKey verifyKey = new VerifyKey(SIGN_PUBLIC, HEX);
        verifyKey.verify(SIGN_MESSAGE, SIGN_SIGNATURE, HEX);
        assertTrue(Arrays.equals(verifyKey.toBytes(), rawPublic));
    }

    @Test
    public void testSerializeToString() throws Exception {
        SigningKey key = new SigningKey(SIGN_PRIVATE, HEX);
        VerifyKey verifyKey = new VerifyKey(SIGN_PUBLIC, HEX);
        verifyKey.verify(SIGN_MESSAGE, SIGN_SIGNATURE, HEX);
        assertEquals(verifyKey.toString(), SIGN_PUBLIC);
    }

    @Test
    public void testInitializeFromHex() throws Exception {
        VerifyKey verifyKey = new VerifyKey(SIGN_PUBLIC, HEX);
        assertTrue(verifyKey.verify(SIGN_MESSAGE, SIGN_SIGNATURE, HEX));
    }

}
