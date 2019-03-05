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
     * @return
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
