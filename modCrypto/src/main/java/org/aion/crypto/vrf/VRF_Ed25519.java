package org.aion.crypto.vrf;

import java.util.Objects;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.util.file.NativeLoader;
import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

public class VRF_Ed25519 {

    static {
        NativeLoader.loadLibrary("sodium");
        NaCl.sodium();
        PROOF_BYTES = Sodium.crypto_vrf_proofbytes();
        PROOF_HASH_BYTES = Sodium.crypto_vrf_outputbytes();
    }

    public final static int PROOF_BYTES;
    private final static int PROOF_HASH_BYTES;


    public static byte[] generateProof(byte[] message, byte[] sk) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(sk);
        if (sk.length != ECKeyEd25519.SECKEY_BYTES) {
            throw new IllegalArgumentException("Invalid private key length");
        }

        byte[] proof = new byte[PROOF_BYTES];
        Sodium.crypto_vrf_prove(proof, sk, message, message.length);

        return proof;
    }

    public static boolean verify(byte[] message, byte[] proof, byte[] publicKey) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(proof);
        Objects.requireNonNull(publicKey);

        if (proof.length != PROOF_BYTES) {
            throw new IllegalArgumentException("Invalid proof length:" + proof.length);
        }

        if (publicKey.length != ECKeyEd25519.PUBKEY_BYTES) {
            throw new IllegalArgumentException("Invalid public key length:" + publicKey.length);
        }

        byte[] hash = new byte[PROOF_HASH_BYTES];
        Sodium.crypto_vrf_proof_to_hash(hash, proof);

        int result = Sodium.crypto_vrf_verify(hash, publicKey, proof, message, message.length);
        return result == 0;
    }
}
