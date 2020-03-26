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

import org.libsodium.jni.encoders.Encoder;
import org.libsodium.jni.SodiumConstants;
import static org.libsodium.jni.NaCl.sodium;

/**
 * @deprecated
 * These are wrapper methods around the sodium api methods.
 * These methods were brought in from another project and will be replaced with method signatures that define exceptions.
 */
@Deprecated
public class Hash {

    private static final int KEY_LEN = 64;
    private static final int SALTBYTES = 32;
    private byte[] buffer;

    public byte[] sha256(byte[] message) {
        buffer = new byte[SodiumConstants.SHA256BYTES];
        sodium().crypto_hash_sha256(buffer, message, message.length);
        return buffer;
    }

    public byte[] sha512(byte[] message) {
        buffer = new byte[SodiumConstants.SHA512BYTES];
        sodium().crypto_hash_sha512(buffer, message, message.length);
        return buffer;
    }

    public String sha256(String message, Encoder encoder) {
        byte[] hash = sha256(message.getBytes());
        return encoder.encode(hash);
    }

    public String sha512(String message, Encoder encoder) {
        byte[] hash = sha512(message.getBytes());
        return encoder.encode(hash);
    }

    public String pwhash_scryptsalsa208sha256(String passwd, Encoder encoder, byte[] salt, int opslimit, int memlimit) {
        buffer = new byte[KEY_LEN];
        //sodium().crypto_pwhash(buffer, buffer.length, passwd.getBytes(), passwd.length(), salt, opslimit, memlimit,sodium().crypto_pwhash_alg_default());

        sodium().crypto_pwhash_scryptsalsa208sha256(buffer, buffer.length, passwd.getBytes(), passwd.length(), salt, opslimit, memlimit);
        return encoder.encode(buffer);
    }

    public byte[] blake2(byte[] message) throws UnsupportedOperationException {
        if (!blakeSupportedVersion()) throw new UnsupportedOperationException();

        buffer = new byte[SodiumConstants.BLAKE2B_OUTBYTES];
        sodium().crypto_generichash_blake2b(buffer, SodiumConstants.BLAKE2B_OUTBYTES, message, message.length, new byte[0], 0);
        return buffer;
    }

    public String blake2(String message, Encoder encoder) throws UnsupportedOperationException {
        if (!blakeSupportedVersion()) throw new UnsupportedOperationException();
        byte[] hash = blake2(message.getBytes());
        return encoder.encode(hash);
    }

    private boolean blakeSupportedVersion(){
	String sodiumversion=new String("0.4.1");
        return sodiumversion.compareTo("0.4.0") >= 0 ;
    }

}
