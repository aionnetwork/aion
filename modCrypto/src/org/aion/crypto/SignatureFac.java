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

import org.aion.crypto.ecdsa.ECDSASignature;
import org.aion.crypto.ecdsa.ECKeySecp256k1;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;

/**
 * Signature factory.
 *
 * @author jin, cleaned by yulong
 */
public class SignatureFac {

    /**
     * @param bytes
     * @return
     */
    public static ISignature fromBytes(byte[] bytes) {
        switch (ECKeyFac.type) {
            case SECP256K1:
                return ECDSASignature.fromComponents(bytes);
            case ED25519:
                return Ed25519Signature.fromBytes(bytes);
            default:
                throw new RuntimeException("ECKey type is not set!");
        }
    }

    /**
     * Verify if the signature is valid or not.
     *
     * @param msg message for signing
     * @param sig the signature
     */
    public static boolean verify(byte[] msg, ISignature sig) {
        switch (ECKeyFac.type) {
            case SECP256K1:
                try {
                    ECDSASignature s = (ECDSASignature) sig;
                    return new ECKeySecp256k1().verify(msg, s, s.getPubkey(msg));
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            case ED25519:
                try {
                    Ed25519Signature s = (Ed25519Signature) sig;
                    return ECKeyEd25519.verify(msg, s.getSignature(), s.getPubkey(null));
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            default:
                throw new RuntimeException("ECKey type is not set!");
        }
    }
}
