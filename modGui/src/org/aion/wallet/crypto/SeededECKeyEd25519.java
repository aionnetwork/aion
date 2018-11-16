/*
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
 */
package org.aion.wallet.crypto;

import java.math.BigInteger;
import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.libsodium.jni.Sodium;

public class SeededECKeyEd25519 extends ECKeyEd25519 {
    private final byte[] publicKey;
    private final byte[] secretKey;
    private final byte[] address;

    private static final int SIG_BYTES = Sodium.crypto_sign_ed25519_seedbytes();

    public SeededECKeyEd25519(final byte[] seed) {
        checkSeed(seed);
        publicKey = new byte[PUBKEY_BYTES];
        secretKey = new byte[SECKEY_BYTES];
        Sodium.crypto_sign_ed25519_seed_keypair(publicKey, secretKey, seed);

        address = computeAddress(publicKey);
    }

    private void checkSeed(final byte[] seed) {
        if (SIG_BYTES != seed.length) {
            throw new IllegalArgumentException(
                    String.format(
                            "Seed has to be exactly %s bytes long, but is %s",
                            SIG_BYTES, seed.length));
        }
    }

    @Override
    public byte[] getAddress() {
        return address;
    }

    @Override
    public byte[] getPubKey() {
        return publicKey;
    }

    @Override
    public byte[] getPrivKeyBytes() {
        return secretKey;
    }

    @Override
    public ECKey fromPrivate(final BigInteger privateKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ECKey fromPrivate(final byte[] bs) {
        throw new UnsupportedOperationException();
    }
}
