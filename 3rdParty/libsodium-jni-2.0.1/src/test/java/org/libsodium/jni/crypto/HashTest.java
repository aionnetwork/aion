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

import org.libsodium.jni.Sodium;
import org.libsodium.jni.NaCl;

import org.junit.Assert;
import org.junit.Test;
import org.libsodium.jni.fixture.TestVectors;

import java.util.Arrays;

import static junit.framework.Assert.assertTrue;
import static org.libsodium.jni.encoders.Encoder.HEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HashTest {

    /**
     * Blake2 test vectors
     */

    public static final String Blake2_Message="Hello world";
    public static final String Blake2_HEXDIGEST="6ff843ba685842aa82031d3f53c48b66326df7639a63d128974c5c14f31a0f33343a8c65551134ed1ae0f2b0dd2bb495dc81039e3eeb0aa1bb0388bbeac29183";

    public static final String Blake2_DIGEST = "01718cec35cd3d796dd00020e0bfecb473ad23457d063b75eff29c0ffa2e58a9";
    public static final String Blake2_DIGEST_EMPTY_STRING = "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce";
    public static final String Blake2_DIGEST_NULL = "2fa3f686df876995167e7c2e5d74c4c7b6e48f8068fe0e44208344d480f7904c36963e44115fe3eb2a3ac8694c28bcb4f5a0f3276f2e79487d8219057a506e4b";    


    public static final String Blake2_KEY = "This is a super secret key. Ssshh!";
    public static final String Blake2_SALT = "0123456789abcdef";
    public static final String Blake2_PERSONAL = "fedcba9876543210";
    public static final String Blake2_DIGEST_WITH_SALT_PERSONAL = "108e81d0c7b0487de45c54554ea35b427f886b098d792497c6a803bbac7a5f7c";

    public static final String HASH_ERR = "Hash is invalid";

    private final Hash hash = new Hash();

    @Test
    public void testGenericHashInit() {
        Sodium sodium= NaCl.sodium();
        byte[] state = new byte[Sodium.crypto_generichash_statebytes()];
        byte[] hash = new byte[Sodium.crypto_generichash_bytes()];
        byte[] key = new byte[Sodium.crypto_generichash_keybytes()];
        Sodium.randombytes(key, key.length);
        assertEquals(0,Sodium.crypto_generichash_init(state, key, key.length, hash.length));

	byte[] message="message".getBytes();
	assertEquals(0,Sodium.crypto_generichash_update(state,message,message.length));
	assertEquals(0,Sodium.crypto_generichash_final(state,hash,Sodium.crypto_generichash_bytes()));
    }

    @Test
    public void testSha256() throws Exception {
        final byte[] rawMessage = TestVectors.SHA256_MESSAGE.getBytes();
        String result = HEX.encode(hash.sha256(rawMessage));
        assertTrue("Hash is invalid", Arrays.equals(TestVectors.SHA256_DIGEST.getBytes(), result.getBytes()));
    }

    @Test
    public void testSha256EmptyString() throws Exception {
        byte[] result = hash.sha256("".getBytes());
        assertEquals("Hash is invalid", TestVectors.SHA256_DIGEST_EMPTY_STRING, HEX.encode(result));
    }

    @Test
    public void testSha256HexString() throws Exception {
        String result = hash.sha256(TestVectors.SHA256_MESSAGE, HEX);
        Assert.assertEquals("Hash is invalid", TestVectors.SHA256_DIGEST, result);
    }

    @Test
    public void testSha256EmptyHexString() throws Exception {
        String result = hash.sha256("", HEX);
        Assert.assertEquals("Hash is invalid", TestVectors.SHA256_DIGEST_EMPTY_STRING, result);
    }

    @Test
    public void testSha256NullByte() {
        try {
            hash.sha256("\0".getBytes());
        } catch (Exception e) {
            fail("Should not raise any exception on null byte");
        }
    }

    @Test
    public void testSha512() throws Exception {
        final byte[] rawMessage = TestVectors.SHA512_MESSAGE.getBytes();
        String result = HEX.encode(hash.sha512(rawMessage));
        assertTrue("Hash is invalid", Arrays.equals(TestVectors.SHA512_DIGEST.getBytes(), result.getBytes()));
    }

    @Test
    public void testSha512EmptyString() throws Exception {
        byte[] result = hash.sha512("".getBytes());
        assertEquals("Hash is invalid", TestVectors.SHA512_DIGEST_EMPTY_STRING, HEX.encode(result));
    }

    @Test
    public void testSha512HexString() throws Exception {
        String result = hash.sha512(TestVectors.SHA512_MESSAGE, HEX);
        Assert.assertEquals("Hash is invalid", TestVectors.SHA512_DIGEST, result);
    }

    @Test
    public void testSha512EmptyHexString() throws Exception {
        String result = hash.sha512("", HEX);
        Assert.assertEquals("Hash is invalid", TestVectors.SHA512_DIGEST_EMPTY_STRING, result);
    }

    @Test
    public void testSha512NullByte() {
        try {
            hash.sha512("\0".getBytes());
        } catch (Exception e) {
            fail("Should not raise any exception on null byte");
        }
    }

    @Test
    public void testBlake2() throws Exception {
        final byte[] rawMessage = Blake2_Message.getBytes();
        String result = HEX.encode(hash.blake2(rawMessage));
	//System.out.println("testBlake2="+result);
	assertEquals(HASH_ERR,Blake2_HEXDIGEST,result);
    }

    @Test
    public void testBlake2EmptyString() throws Exception {
        byte[] result = hash.blake2("".getBytes());
	//System.out.println("testBlake2EmptyString="+HEX.encode(result));
	assertEquals(HASH_ERR, Blake2_DIGEST_EMPTY_STRING, HEX.encode(result));
    }

    @Test
    public void testBlake2HexString() throws Exception {
        String result = hash.blake2(Blake2_Message, HEX);
	//System.out.println("testBlake2HexString="+result);
	assertEquals(HASH_ERR,Blake2_HEXDIGEST,result);
    }

    @Test
    public void testBlake2EmptyHexString() throws Exception {
        String result = hash.blake2("", HEX);
	//System.out.println("testBlake2EmptyHexString="+result);
        assertEquals(HASH_ERR, Blake2_DIGEST_EMPTY_STRING, result);
    }

    @Test
    public void testBlake2NullByte() {
	byte[] result=hash.blake2("\0".getBytes());
	//System.out.println("testBlake2NullByte="+HEX.encode(result));
	assertEquals(HASH_ERR, Blake2_DIGEST_NULL, HEX.encode(result));
    }

    @Test
    public void testGenericHash() {
        Sodium sodium= NaCl.sodium();
        byte[] hash = new byte[Sodium.crypto_generichash_bytes()];
        byte[] message = "message".getBytes();
        byte[] key = new byte[Sodium.crypto_generichash_keybytes()];
        Sodium.randombytes(key, key.length);
        assertEquals(0,Sodium.crypto_generichash(hash,Sodium.crypto_generichash_bytes(),message,message.length,key,key.length));
    }
}
