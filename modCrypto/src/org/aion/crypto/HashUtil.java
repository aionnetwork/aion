/*******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.crypto;

import org.aion.base.util.NativeLoader;
import org.aion.crypto.hash.Blake2b;
import org.aion.crypto.hash.Blake2bNative;
import org.aion.rlp.RLP;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.KeccakDigest;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static java.util.Arrays.copyOfRange;
import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;

/**
 * A collection of utility functions for computing hashes.
 * <p>
 * It's recommended to use {@link #h256(byte[])}, {@link #h256(byte[], byte[])}
 * and {@link #h256(byte[], int, int)} whenever possible, instead of using the
 * specific hash algorithms
 * </p>
 *
 * @author jin, cleaned by yulong
 */
public class HashUtil {

    static {
        NativeLoader.loadLibrary("blake2b");
    }

    public enum H256Type {
        KECCAK_256, BLAKE2B_256
    }

    protected static H256Type type = BLAKE2B_256;

    public static final byte[] EMPTY_DATA_HASH = h256(EMPTY_BYTE_ARRAY);
    public static final byte[] EMPTY_LIST_HASH = h256(RLP.encodeList());
    public static final byte[] EMPTY_TRIE_HASH = h256(RLP.encodeElement(EMPTY_BYTE_ARRAY));

    /**
     * Sets the 256-bit hash type.
     *
     * @param type
     */
    public static void setType(H256Type type) {
        HashUtil.type = type;
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
                return blake256Native(in);
            case KECCAK_256:
                return keccak256(in);
            default:
                throw new RuntimeException("h256 hash type is not set!");
        }
    }

    /**
     * Computes the 256-bit hash of the given two inputs.
     */
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

        if (in == null || start < 0 || len <= 0)
            return null;

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
     * Computes the SHA-256, a member of the SHA-2 cryptographic hash functions,
     * of the given input.
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
     * Added in blake2b equivalent of retrieving the hash of two hashes, to be
     * used by trie implementations
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

    public static byte[][] getSolutionHash(byte[] personalization, byte[] nonce, int[] indices, byte[] header) {
        return Blake2bNative.getSolutionHash(personalization, nonce, indices, header);
    }

    /**
     * blake2b 128-bit digest variant
     *
     * @param in a variable length input to be hashed
     * @return {@code hash} 128-bit (16 byte) output from blake2b hashing
     * algorithm
     */
    public static byte[] blake128(byte[] in) {
        Blake2b digest = Blake2b.Digest.newInstance(16);
        digest.update(in);
        return digest.digest();
    }

    /**
     * Computes the ripemed160 hash fo the given input data.
     *
     * @param data Message to hash
     * @return reipmd160 hash of the message
     */
    public static byte[] ripemd160(byte[] data) {
        Digest digest = new RIPEMD160Digest();
        if (data != null) {
            byte[] resBuf = new byte[digest.getDigestSize()];
            digest.update(data, 0, data.length);
            digest.doFinal(resBuf, 0);
            return resBuf;
        }
        throw new NullPointerException("Can't hash a NULL value");
    }

    /**
     * Computes the SHA3 hash of the input.
     * <p>
     * NOTE: This method does NOT follow the NIST SHA3-256 standard, it's only
     * an alias of the {@link #keccak256(byte[])} function, and thus being
     * deprecated.
     * </p>
     *
     * @param input
     * @return
     */
    @Deprecated
    public static byte[] sha3(byte[] input) {
        return keccak256(input);
    }

    /**
     * Calculates RIGTMOST160(SHA3(input)). This is used in address
     * calculations. * @param input - data
     *
     * @return - 20 right bytes of the hash keccak of the data
     */
    public static byte[] keccak256omit12(byte[] input) {
        byte[] hash = keccak256(input);
        return copyOfRange(hash, 12, hash.length);
    }

    /**
     * Calculates the address as per the QA2 definitions
     */
    public static byte[] calcNewAddr(byte[] addr, byte[] nonce) {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.put(AddressSpecs.A0_IDENTIFIER);

        byte[] encSender = RLP.encodeElement(addr);
        byte[] encNonce = RLP.encodeBigInteger(new BigInteger(1, nonce));

        buf.put(h256(RLP.encodeList(encSender, encNonce)), 1, 31);
        return buf.array();
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
