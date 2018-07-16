package org.aion.wallet.util;

import io.github.novacrypto.bip39.SeedCalculator;
import org.aion.crypto.ECKey;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.wallet.exception.ValidationException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public final class CryptoUtils {

    private static final byte[] ED25519_KEY = "ed25519 seed".getBytes();
    private static final String HMAC_SHA512_ALGORITHM = "HmacSHA512";
    private static final String DEFAULT_MNEMONIC_PASSPHRASE = "";
    private static final int HARDENED_KEY_MULTIPLIER = 0x80000000;
    private static final ECKeyEd25519 EC_KEY_FACTORY = new ECKeyEd25519();

    private CryptoUtils() {}

    public static ECKey getBip39ECKey(final String mnemonic) throws ValidationException {
        final byte[] seed = new SeedCalculator().calculateSeed(mnemonic, DEFAULT_MNEMONIC_PASSPHRASE);
        return getECKey(getSha512(ED25519_KEY, seed));

    }

    public static byte[] getSha512(final byte[] secret, final byte[] hashData) throws ValidationException {
        final byte[] bytes;
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA512_ALGORITHM);
            final SecretKey key = new SecretKeySpec(secret, HMAC_SHA512_ALGORITHM);
            mac.init(key);
            bytes = mac.doFinal(hashData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ValidationException(e);
        }
        return bytes;
    }

    public static byte[] getHardenedNumber(final int number) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.putInt(number | HARDENED_KEY_MULTIPLIER);
        return byteBuffer.array();
    }

    public static ECKey getECKey(final byte[] privateKey) {
        return EC_KEY_FACTORY.fromPrivate(privateKey);
    }
}
