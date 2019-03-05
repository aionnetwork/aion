package org.aion.crypto;

/**
 * The interface for EC signature.
 *
 * @author jin, cleaned by yulong
 */
public interface ISignature {

    /**
     * Converts into a byte array.
     *
     * @return
     */
    byte[] toBytes();

    /** Returns the raw signature. */
    byte[] getSignature();

    /**
     * Returns the public key, encoded or recovered.
     *
     * @param msg Only required by Secp256k1; pass null if you're using ED25519
     * @return
     */
    byte[] getPubkey(byte[] msg);

    /** Recovers the address of the account given the signature */
    byte[] getAddress();
}
