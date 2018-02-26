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

import java.util.ArrayList;
import java.util.Arrays;

import org.aion.crypto.hash.Blake2b;
import org.aion.crypto.hash.Blake2b.Param;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import static org.aion.base.util.ByteUtil.*;

/**
 * 
 * This class provides methods to validate Equihash solutions.
 * 
 * @author Ross Kitsis
 *
 */
public class EquiValidator {
    private int n;
    private int k;
    private int indicesPerHashOutput;
    private int hashOutput;
    private int collisionBitLength;
    private int collisionByteLength;
    private int hashLength;
    private int finalFullWidth;
    private int solutionWidth;
    private int indicesHashLength;
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    public EquiValidator(int n, int k) {
        this.n = n;
        this.k = k;
        this.indicesPerHashOutput = 512 / n;
        this.indicesHashLength = (n + 7) / 8;
        this.hashOutput = indicesPerHashOutput * indicesHashLength;
        this.collisionBitLength = n / (k + 1);
        this.collisionByteLength = (collisionBitLength + 7) / 8;
        this.hashLength = (k + 1) * collisionByteLength;
        this.finalFullWidth = 2 * collisionByteLength + (Integer.BYTES * (1 << k));
        this.solutionWidth = (1 << k) * (collisionBitLength + 1) / 8;
    }

    /**
     * Initialize Equihash parameters; current implementation uses default
     * equihash parameters. Set Personalization to "AION0PoW" + n to k where n
     * and k are in little endian byte order.
     * 
     * @return a Param object containing Blake2b parameters.
     */
    private Param InitialiseState() {
        Param p = new Param();
        byte[] personalization = merge("AION0PoW".getBytes(), merge(intToBytesLE(n), intToBytesLE(k)));
        p.setPersonal(personalization);
        p.setDigestLength(hashOutput);

        return p;
    }

    /**
     * Determines if a received solution is valid.
     * 
     * @param solution
     *            The solution in minimal form.
     * @param blockHeader
     *            The block header.
     * @param nonce
     *            The nonce for the solution.
     * @return True if the solution is valid for the blockHeader and nonce.
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
        // Create array with 2^k slots
        FullStepRow[] X = new FullStepRow[1 << k];
        byte[] tmpHash;
        int j = 0;
        for (int i : getIndicesFromMinimal(solution, collisionBitLength)) {

            // Initialize blake2b digest with personalization params
            Blake2b blake = Blake2b.Digest.newInstance(InitialiseState());

            // Build H(I | V ...

            // I = block header minus nonce and solution
            blake.update(blockHeader, 0, blockHeader.length);

            // V = nonce
            blake.update(nonce, 0, nonce.length);

            byte[] x = intToBytesLE(i / indicesPerHashOutput);

            blake.update(x, 0, x.length);

            tmpHash = blake.digest();

            X[j] = new FullStepRow(finalFullWidth,
                    Arrays.copyOfRange(tmpHash, (i % indicesPerHashOutput) * indicesHashLength,
                            ((i % indicesPerHashOutput) * indicesHashLength) + hashLength),
                    indicesHashLength, hashLength, collisionBitLength, i);
            j++;

        }

        int hashLen = hashLength;
        int lenIndices = Integer.BYTES;
        while (X.length > 1) {
            ArrayList<FullStepRow> Xc = new ArrayList<>();
            for (int i = 0; i < X.length; i = i + 2) {

                if (!hasCollision(X[i], X[i + 1], collisionByteLength)) {
                    LOG.error("Invalid Solution: Collision not present");
                    return false;
                }

                if (EquiUtils.indicesBefore(X[i + 1], X[i], hashLen, lenIndices)) {
                    LOG.error("Invalid Solution: Index tree incorrecly ordered");
                    return false;
                }
                if (!distinctIndices(X[i + 1], X[i], hashLen, lenIndices)) {
                    LOG.error("Invalid solution: duplicate indices");
                    return false;
                }

                // Check order of X[i] and X[i+1] in because indices before is
                // called in the constructor
                Xc.add(new FullStepRow(finalFullWidth, X[i], X[i + 1], hashLen, lenIndices, collisionByteLength));

            }
            FullStepRow[] temp = new FullStepRow[Xc.size()];

            for (int i = 0; i < Xc.size(); i++) {
                temp[i] = Xc.get(i);
            }
            X = temp;
            hashLen -= collisionByteLength;
            lenIndices *= 2;
        }

        return X[0].isZero(hashLen);
    }

    /**
     * Get indices of solutions from minimized array format.
     * 
     * @param minimal
     *            Byte array in minimal format
     * @param cBitLen
     *            Number of bits in a collision
     * @return An array containing solution indices.
     */
    public int[] getIndicesFromMinimal(byte[] minimal, int cBitLen) throws NullPointerException {
        if (minimal == null) {
            throw new NullPointerException("null minimal bytes");
        }

        int lenIndices = 8 * Integer.BYTES * minimal.length / (cBitLen + 1);
        int bytePad = Integer.BYTES - ((cBitLen + 1) + 7) / 8;

        byte[] arr = new byte[lenIndices];
        EquiUtils.extendArray(minimal, arr, cBitLen + 1, bytePad);

        return bytesToInts(arr, true);
    }

    /**
     * Determines if the hashes of A and B have collisions on length l
     * 
     * @param a
     *            StepRow A
     * @param b
     *            StepRow B
     * @param l
     *            Length of bytes to compare
     * @return False if no collision in hashes a,b up to l, else true.
     */
    public boolean hasCollision(StepRow a, StepRow b, int l) throws NullPointerException {
        if (a == null || b == null) {
            throw new NullPointerException("null StepRow passed");
        }

        for (int j = 0; j < l; j++) {
            if (a.getHash()[j] != b.getHash()[j]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compare indices and ensure the intersection of hashes is empty
     * 
     * @param a
     *            StepRow a
     * @param b
     *            StepRow b
     * @param len
     *            Number of elements to compare
     * @param lenIndices
     *            Number of indices to compare
     * @return true if distinct; false otherwise
     */
    public boolean distinctIndices(FullStepRow a, FullStepRow b, int len, int lenIndices) throws NullPointerException {
        if (a == null || b == null) {
            throw new NullPointerException("null FullStepRow passed");
        }

        for (int i = 0; i < lenIndices; i = i + Integer.BYTES) {
            for (int j = 0; j < lenIndices; j = j + Integer.BYTES) {
                if (Arrays.compare(Arrays.copyOfRange(a.getHash(), len + i, len + i + Integer.BYTES),
                        Arrays.copyOfRange(b.getHash(), len + j, len + j + Integer.BYTES)) == 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
