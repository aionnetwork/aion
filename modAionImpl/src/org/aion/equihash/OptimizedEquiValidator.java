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
 * Contributors:
 *     Aion foundation.
 *
 ******************************************************************************/
package org.aion.equihash;

import org.aion.crypto.HashUtil;
import org.aion.crypto.hash.Blake2b;
import org.aion.crypto.hash.Blake2bNative;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.aion.base.util.ByteUtil.*;

public class OptimizedEquiValidator {
    private int n;
    private int k;
    private int indicesPerHashOutput;
    private int indicesHashLength;
    private int hashOutput;
    private int collisionBitLength;
    private int collisionByteLength;
    private int solutionWidth;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private Blake2b.Param initState;

    private byte[][] hashes;

    public OptimizedEquiValidator(int n, int k) {
        this.n = n;
        this.k = k;
        this.indicesPerHashOutput = 512 / n;
        this.indicesHashLength = (n + 7) / 8;
        this.hashOutput = indicesPerHashOutput * indicesHashLength;
        this.collisionBitLength = n / (k + 1);
        this.collisionByteLength = (collisionBitLength + 7) / 8;
        this.solutionWidth = (1 << k) * (collisionBitLength + 1) / 8;
        this.initState = this.InitialiseState();
        this.hashes = new byte[512][indicesHashLength];
        //this.indexSet = new HashSet<>();
    }

    /**
     * Initialize Equihash parameters; current implementation uses default
     * equihash parameters. Set Personalization to "AION0PoW" + n to k where n
     * and k are in little endian byte order.
     *
     * @return a Param object containing Blake2b parameters.
     */
    private Blake2b.Param InitialiseState() {
        Blake2b.Param p = new Blake2b.Param();
        byte[] personalization = merge("AION0PoW".getBytes(), merge(intToBytesLE(n), intToBytesLE(k)));
        p.setPersonal(personalization);
        p.setDigestLength(hashOutput);

        return p;
    }

    /**
     * Validate a solution for a given block header and nonce
     *
     * @param solution Byte array containing the Equihash solution
     * @param blockHeader Block header raw bytes excluding nonce and solution
     * @param nonce Nonce used to generate solution
     * @return True if valid solution based on block header and nonce
     * @throws NullPointerException
     */
    public boolean isValidSolution(byte[] solution, byte[] blockHeader, byte[] nonce) throws NullPointerException {
        if (solution == null) {
            LOG.debug("Null solution passed for validation");
            throw new NullPointerException("Null solution");
        } else if (blockHeader == null) {
            LOG.debug("Null blockHeader passed for validation");
            throw new NullPointerException("Null blockHeader");
        } else if (nonce == null) {
            LOG.debug("Null nonce passed for validation");
            throw new NullPointerException("Null nonce");
        }

        if (solution.length != solutionWidth) {
            LOG.debug("Invalid solution width: {}", solution.length);
            return false;
        }

        Blake2b blake = Blake2b.Digest.newInstance(initState);

        int[] indices = EquiUtils.getIndicesFromMinimal(solution, collisionBitLength);

        if(hasDuplicate(indices)) {
            LOG.debug("Invalid solution - duplicate solution index");
            return false;
        }

        byte[] hash = new byte[indicesHashLength];

        return verify(blockHeader, nonce, blake, indices, 0, hash, k);
    }

    public boolean isValidSolutionNative(byte[] solution, byte[] blockHeader, byte[] nonce) throws NullPointerException {
        if (solution == null) {
            LOG.debug("Null solution passed for validation");
            throw new NullPointerException("Null solution");
        } else if (blockHeader == null) {
            LOG.debug("Null blockHeader passed for validation");
            throw new NullPointerException("Null blockHeader");
        } else if (nonce == null) {
            LOG.debug("Null nonce passed for validation");
            throw new NullPointerException("Null nonce");
        }

        if (solution.length != solutionWidth) {
            LOG.debug("Invalid solution width: {}", solution.length);
            return false;
        }

        int[] indices = EquiUtils.getIndicesFromMinimal(solution, collisionBitLength);

        if (hasDuplicate(indices)) {
            LOG.debug("Invalid solution - duplicate solution index");
            return false;
        }

        byte[] hash = new byte[indicesHashLength];


        byte[][] nativeHash = HashUtil.getSolutionHash(
                merge("AION0PoW".getBytes(), merge(intToBytesLE(n), intToBytesLE(k))),
                nonce,
                indices,
                blockHeader
        );

        return verifyNative(indices, 0, hash, k, nativeHash);
    }

    /**
     * Generate hash based on indices and index.
     * The generated hash is placed in the hashes array based on the index
     */
    private void genHash(byte[] blockHeader, byte[] nonce, Blake2b blake, int[] indices, int index, byte[] hash) {

        // Clear blake and re-use
        blake.reset();

        // I = block header minus nonce and solution
        blake.update(blockHeader, 0, blockHeader.length);

        // V = nonce
        blake.update(nonce, 0, nonce.length);

        byte[] x = intToBytesLE(indices[index] / indicesPerHashOutput);

        blake.update(x, 0, x.length);

        byte[] tmpHash = blake.digest();

        System.arraycopy(tmpHash, (indices[index] % indicesPerHashOutput) * indicesHashLength, hash, 0, indicesHashLength);
    }

    private boolean verify(byte[] blockHeader, byte[] nonce, Blake2b blake, int[] indices, int index, byte[] hash, int round) {
        if(round == 0) {
            //Generate hash
            genHash(blockHeader, nonce, blake, indices, index, hash);
            return true;
        }

        int index1 = index + (1 << (round-1));

        // Check out of order indices
        if(indices[index] >= indices[index1]) {
            LOG.debug("Solution validation failed - indices out of order");
            return false;
        }

        byte[] hash0 = new byte[indicesHashLength];
        byte[] hash1 = new byte[indicesHashLength];

        boolean verify0 = verify(blockHeader, nonce, blake, indices, index, hash0, round-1);
        if(!verify0) {
            LOG.debug("Solution validation failed - unable to verify left subtree");
            return false;
        }

        boolean verify1 = verify(blockHeader, nonce, blake, indices, index1, hash1, round-1);
        if(!verify1) {
            LOG.debug("Solution validation failed - unable to verify right subtree");
            return false;
        }

        for(int i = 0; i < indicesHashLength; i++)
            hash[i] = (byte)(hash0[i] ^ hash1[i]);

        int bits = (round < k ? round * collisionBitLength : n);

        for(int i = 0; i < bits/8; i++) {
            if(hash[i] != 0) {
                LOG.debug("Solution validation failed - Non-zero XOR");
                return false;
            }
        }

        // Check remainder bits
        if((bits%8) > 0 && (hash[bits/8] >> (8 - (bits%8)) ) != 0) {
            LOG.debug("Solution validation failed - Non-zero XOR");
            return false;
        }

        return true;
    }

    /*
    Validation based on hashes generated by native blake2b impl
     */
    private boolean verifyNative(int[] indices, int index, byte[] hash, int round, byte[][] hashes) {
        if(round == 0) {
            return true;
        }

        int index1 = index + (1 << (round-1));

        // Check out of order indices
        if(indices[index] >= indices[index1]) {
            LOG.debug("Solution validation failed - indices out of order");
            return false;
        }

        byte[] hash0 = hashes[index];
        byte[] hash1 = hashes[index1];

        boolean verify0 = verifyNative(indices, index, hash0, round-1, hashes);
        if(!verify0) {
            LOG.debug("Solution validation failed - unable to verify left subtree");
            return false;
        }

        boolean verify1 = verifyNative(indices, index1, hash1, round-1, hashes);
        if(!verify1) {
            LOG.debug("Solution validation failed - unable to verify right subtree");
            return false;
        }

        for(int i = 0; i < indicesHashLength; i++)
            hash[i] = (byte)(hash0[i] ^ hash1[i]);

        int bits = (round < k ? round * collisionBitLength : n);

        for(int i = 0; i < bits/8; i++) {
            if(hash[i] != 0) {
                LOG.debug("Solution validation failed - Non-zero XOR");
                return false;
            }
        }

        // Check remainder bits
        if((bits%8) > 0 && (hash[bits/8] >> (8 - (bits%8)) ) != 0) {
            LOG.debug("Solution validation failed - Non-zero XOR");
            return false;
        }

        return true;
    }


    /*
     * Check if duplicates are present in the solutions index array
     */
    private boolean hasDuplicate(int[] indices) {
        //TODO: Switch this to a threadLocal implementation; avoid for now until threading strategy is determined.
        Set<Integer> indexSet = new HashSet<>(512);

        for(int index: indices) {
            if(!indexSet.add(index))
                return true;
        }

        return false;
    }
}
