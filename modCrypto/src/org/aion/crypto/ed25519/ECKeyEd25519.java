/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/
package org.aion.crypto.ed25519;

import org.aion.base.util.NativeLoader;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ED25519 key implementation based on libsodium.
 *
 * @author yulong
 */
public class ECKeyEd25519 implements ECKey {

    protected static int PUBKEY_BYTES;
    protected static int SECKEY_BYTES;
    protected static int SIG_BYTES;

    static {
        NativeLoader.loadLibrary("sodium");
        NaCl.sodium();
        PUBKEY_BYTES = Sodium.crypto_sign_ed25519_publickeybytes();
        SECKEY_BYTES = Sodium.crypto_sign_ed25519_secretkeybytes();
        SIG_BYTES = Sodium.crypto_sign_ed25519_bytes();
    }

    private byte[] pk;
    private byte[] sk;

    public ECKeyEd25519(byte[] pk, byte[] sk) {
        this.pk = pk;
        this.sk = sk;
    }

    public ECKeyEd25519() {
        pk = new byte[PUBKEY_BYTES];
        sk = new byte[SECKEY_BYTES];
        Sodium.crypto_sign_ed25519_keypair(pk, sk);
    }

    public ECKey fromPrivate(BigInteger privKey) {
        throw new UnsupportedOperationException();
    }

    public ECKey fromPrivate(byte[] bs) {
        byte[] pk = new byte[PUBKEY_BYTES];
        byte[] sk = bs;
        Sodium.crypto_sign_ed25519_sk_to_pk(pk, sk);
        return new ECKeyEd25519(pk, sk);
    }

    public byte[] computeAddress(byte[] pubBytes) {
        return pubBytes;
    }

    public byte[] getAddress() {
        return computeAddress(pk);
    }

    public byte[] getPubKey() {
        return pk;
    }

    /**
     * Signs a message with this key.
     *
     * @param msg
     * @return
     */
    public ISignature sign(byte[] msg) {
        byte[] sig = new byte[SIG_BYTES];
        int[] sigLen = new int[1];

        int ret = Sodium.crypto_sign_ed25519_detached(sig, sigLen, msg, msg.length, sk);
        if (ret != 0) {
            throw new RuntimeException("Failed to sign message!");
        }

        return new Ed25519Signature(pk, sig);
    }

    /**
     * Verifies if a signature is valid or not.
     *
     * @param msg
     * @param sig
     * @param pk
     * @return
     */
    public static boolean verify(byte[] msg, byte[] sig, byte[] pk) {
        if (msg == null || sig == null || pk == null) {
            return false;
        }

        return 0 == Sodium.crypto_sign_ed25519_verify_detached(sig, msg, msg.length, pk);
    }

    public byte[] getPrivKeyBytes() {
        return sk;
    }

    @Override
    public String toString() {
        return "pub:" + Hex.toHexString(pk);
    }

}
