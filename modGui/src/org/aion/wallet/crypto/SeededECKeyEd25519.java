package org.aion.wallet.crypto;

import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.libsodium.jni.Sodium;

import java.math.BigInteger;

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
            throw new IllegalArgumentException(String.format("Seed has to be exactly %s bytes long, but is %s", SIG_BYTES, seed.length));
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
