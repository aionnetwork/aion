package org.aion.wallet.crypto;

import org.aion.crypto.ECKey;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.util.CryptoUtils;

import java.util.Arrays;

public class MasterKey {

    private final ECKey ecKey;

    public MasterKey(ECKey ecKey) {
        this.ecKey = ecKey;
    }

    public ECKey getEcKey() {
        return ecKey;
    }

    public ECKey deriveHardened(int[] derivationPath) throws ValidationException {
        if (derivationPath.length == 0) {
            throw new ValidationException("Derivation path is incorrect");
        }
        byte[] key = ecKey.getPrivKeyBytes();
        for (final int pathElement : derivationPath) {
            key = getChild(pathElement, key);
        }
        final byte[] seed = Arrays.copyOfRange(key, 0, 32);
        return new SeededECKeyEd25519(seed);

    }

    private byte[] getChild(final int pathElement, final byte[] keyHash) throws ValidationException {
        byte[] parentPrivateKey = Arrays.copyOfRange(keyHash, 0, 32);
        byte[] parentChainCode = Arrays.copyOfRange(keyHash, 32, 64);

        // ed25519 supports ONLY hardened keys
        final byte[] offset = CryptoUtils.hardenedNumber(pathElement);

        byte[] parentPaddedKey = org.spongycastle.util.Arrays.concatenate(new byte[]{0}, parentPrivateKey, offset);

        return CryptoUtils.hashSha512(parentChainCode, parentPaddedKey);
    }
}
