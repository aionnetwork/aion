package org.aion.crypto;

import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.aion.crypto.hash.Blake2b;
import org.aion.crypto.hash.Blake2bNative;
import org.aion.crypto.hash.Blake2bSodium;
import org.aion.util.file.NativeLoader;
import org.spongycastle.crypto.digests.KeccakDigest;
import org.spongycastle.util.encoders.Hex;

/**
 * A collection of utility functions for computing hashes.
 *
 * <p>It's recommended to use {@link #h256(byte[])}, {@link #h256(byte[], byte[])} and {@link
 * #h256(byte[], int, int)} whenever possible, instead of using the specific hash algorithms
 *
 * @author jin, cleaned by yulong
 */
public class HashUtil {

    static {
        NativeLoader.loadLibrary("blake2b");
    }

    public enum H256Type {
        KECCAK_256,
        BLAKE2B_256
    }

    protected static H256Type type = BLAKE2B_256;

    public static final byte[] EMPTY_DATA_HASH = h256(EMPTY_BYTE_ARRAY);

    private static boolean beforeSignatureSwap = true;

    /**
     * Sets the 256-bit hash type.
     *
     * @param type
     */
    public static void setType(H256Type type) {
        HashUtil.type = type;
    }

    // AKI-716
    public static void setAfterSignatureSwap() {
        beforeSignatureSwap = false;
    }
    public static void setBeforeSignatureSwap() {
        beforeSignatureSwap = true;
    }


    /**
     * Computes the 256-bit hash of the given input.
     *
     * @param in
     * @return
     */
    public static byte[] h256(byte[] in) {

        if (in == null) {
            return null;
        }

        switch (type) {
            case BLAKE2B_256:
                return beforeSignatureSwap ? blake256Native(in) : Blake2bSodium.blake256(in);
            case KECCAK_256:
                return keccak256(in);
            default:
                throw new RuntimeException("h256 hash type is not set!");
        }
    }

    /** Computes the 256-bit hash of the given two inputs. */
    public static byte[] h256(byte[] in1, byte[] in2) {

        if (in1 == null || in2 == null) {
            return null;
        }

        switch (type) {
            case BLAKE2B_256:
                return blake256Native(in1, in2);
            case KECCAK_256:
                return keccak256(in1, in2);
            default:
                throw new RuntimeException("h256 hash type is not set!");
        }
    }

    /**
     * Computes the 256 hash of part of the given input.
     *
     * @param in
     * @param start
     * @param len
     * @return
     */
    public static byte[] h256(byte[] in, int start, int len) {

        if (in == null || start < 0 || len <= 0) return null;

        byte[] buf = Arrays.copyOfRange(in, start, start + len);
        switch (type) {
            case BLAKE2B_256:
                return blake256(buf);
            case KECCAK_256:
                return keccak256(buf);
            default:
                throw new RuntimeException("h256 hash type is not set!");
        }
    }

    /**
     * Computes the SHA-256, a member of the SHA-2 cryptographic hash functions, of the given input.
     *
     * @param input Data for hashing
     * @return Hash of the data
     */
    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest sha256digest = MessageDigest.getInstance("SHA-256");
            return sha256digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes the keccak-256 hash of the given input.
     *
     * @param input Data for hashing
     * @return Hash
     */
    public static byte[] keccak256(byte[] input) {
        KeccakDigest digest = new KeccakDigest(256);

        digest.update(input, 0, input.length);

        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        return hash;
    }

    /**
     * Computes the keccak-256 hash of the given two inputs.
     *
     * @param input1
     * @param input2
     * @return
     */
    public static byte[] keccak256(byte[] input1, byte[] input2) {
        KeccakDigest digest = new KeccakDigest(256);

        digest.update(input1, 0, input1.length);
        digest.update(input2, 0, input2.length);

        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        return hash;
    }

    /**
     * Computes the blake2b-256 hash of the given input.
     *
     * @param input Data for hashing
     * @return Hash
     */
    public static byte[] blake256(byte[] input) {
        Blake2b digest = Blake2b.Digest.newInstance(32);
        digest.update(input);
        return digest.digest();
    }

    /**
     * Added in blake2b equivalent of retrieving the hash of two hashes, to be used by trie
     * implementations
     *
     * @param in1
     * @param in2
     * @return
     */
    public static byte[] blake256(byte[] in1, byte[] in2) {
        Blake2b digest = Blake2b.Digest.newInstance(32);
        digest.update(in1);
        digest.update(in2);
        return digest.digest();
    }

    public static byte[] blake256Native(byte[] in) {
        return Blake2bNative.blake256(in);
    }

    public static byte[] blake256Native(byte[] in1, byte[] in2) {
        return Blake2bNative.blake256(in1, in2);
    }

    public static byte[][] getSolutionHash(
            byte[] personalization, byte[] nonce, int[] indices, byte[] header) {
        return Blake2bNative.getSolutionHash(personalization, nonce, indices, header);
    }

    /**
     * blake2b 128-bit digest variant
     *
     * @param in a variable length input to be hashed
     * @return {@code hash} 128-bit (16 byte) output from blake2b hashing algorithm
     */
    public static byte[] blake128(byte[] in) {
        Blake2b digest = Blake2b.Digest.newInstance(16);
        digest.update(in);
        return digest.digest();
    }

    /**
     * Returns the first 3 bytes of the hash, represented in hex string.
     *
     * @param hash
     * @return
     */
    public static String shortHash(byte[] hash) {
        return Hex.toHexString(hash).substring(0, 6);
    }
}
