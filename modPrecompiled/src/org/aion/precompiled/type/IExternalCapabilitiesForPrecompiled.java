package org.aion.precompiled.type;

/**
 * An external capabilities interface in which the caller passes in capabilities to Precompiled.
 */
public interface IExternalCapabilitiesForPrecompiled {

    /**
     * Verifies the transaction hash against the signature.
     *
     * Returns true if they correspond
     *
     * @param hash The hash of the transaction.
     * @param signature The signature as a byte array.
     * @return true if the signature is valid
     */
    boolean verifyISig(byte[] hash, byte[] signature);

    byte[] blake2b(byte[] bytes);

    byte[] keccak256(byte[] bytes);

    boolean verifyEd25519Signature(byte[] bytes, byte[] bytes1, byte[] bytes2);

    boolean verifyEd25519Signature(byte[] bytes, byte[] signature);

    byte[] getEd25519Address(byte[] signature);

    byte[] getISigAddress(byte[] signature);

    long calculateTransactionCost(byte[] data, boolean isCreate);
}
