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
package org.aion.crypto;

import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;

import org.aion.crypto.ecdsa.ECKeySecp256k1;
import org.aion.crypto.ed25519.ECKeyEd25519;

/**
 * Factory class that generates key.
 *
 * @author jin, cleaned by yulong
 */
public class ECKeyFac {

    public enum ECKeyType {
        SECP256K1,
        ED25519
    }

    protected static ECKeyType type = ED25519;

    /**
     * Sets the signature algorithm type.
     *
     * @param t
     */
    public static void setType(ECKeyType t) {
        type = t;
    }

    private static class ECKeyFacHolder {
        private static final ECKeyFac INSTANCE = new ECKeyFac();
    }

    private ECKeyFac() {}

    /**
     * Returns the ECKey factory singleton instance.
     *
     * @return
     */
    public static ECKeyFac inst() {
        return ECKeyFacHolder.INSTANCE;
    }

    /**
     * Creates a random key pair.
     *
     * @return
     */
    public ECKey create() {
        switch (type) {
            case SECP256K1:
                return new ECKeySecp256k1();
            case ED25519:
                return new ECKeyEd25519();
            default:
                throw new RuntimeException("ECKey type is not set!");
        }
    }

    /**
     * Recovers a key pair from the private key.
     *
     * @param pk
     * @return
     */
    public ECKey fromPrivate(byte[] pk) {
        switch (type) {
            case SECP256K1:
                return new ECKeySecp256k1().fromPrivate(pk);
            case ED25519:
                return new ECKeyEd25519().fromPrivate(pk);
            default:
                throw new RuntimeException("ECKey type is not set!");
        }
    }
}
