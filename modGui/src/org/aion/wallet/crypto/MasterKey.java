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
        byte[] parentPrivateKey;
        byte[] parentChainCode;
        try {
            parentPrivateKey = Arrays.copyOfRange(keyHash, 0, 32);
            parentChainCode = Arrays.copyOfRange(keyHash, 32, 64);
        } catch (ArrayIndexOutOfBoundsException oob) {
            throw new ValidationException(oob);
        }

        // ed25519 supports ONLY hardened keys
        final byte[] offset = CryptoUtils.hardenedNumber(pathElement);

        byte[] parentPaddedKey = org.spongycastle.util.Arrays.concatenate(new byte[]{0}, parentPrivateKey, offset);

        return CryptoUtils.hashSha512(parentChainCode, parentPaddedKey);
    }
}
