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
package org.aion.equihash;

import static org.aion.base.util.ByteUtil.merge;
import static org.aion.base.util.ByteUtil.toLEByteArray;
import static org.aion.base.util.Hex.toHexString;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.aion.base.util.NativeLoader;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

/**
 * This class serves as the front end interface to the Tromp Equihash solver accessed through JNI.
 * This class also contains methods to verify equihash solutions, either generated locally or
 * received from peers.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class Equihash {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    /*
     * Load native libraries
     */
    static {
        NativeLoader.loadLibrary("sodium");
        NativeLoader.loadLibrary("equihash");
    }

    AtomicLong totalSolGenerated;
    private int cBitLen; // Collision Bit Length used by equihash

    /**
     * Create a new Equihash instance with the parameters (n,k)
     *
     * @param n Total number of bits over which to do XOR collisions
     * @param k Number of steps with which to solve.
     */
    public Equihash(int n, int k) {
        this.cBitLen = n / (k + 1);
        this.totalSolGenerated = new AtomicLong(0);
    }

    public native int[][] solve(byte[] nonce, byte[] headerBytes);

    /**
     * Retrieves a set of possible solutions given the passed header and nonce value Any number of
     * solutions may be returned; the maximum number of solutions observed has been 8
     *
     * @param header A 32 byte hash of the block header (minus nonce and solutions)
     * @param nonce - A 32 byte header
     * @return An array of equihash solutions
     */
    public int[][] getSolutionsForNonce(byte[] header, byte[] nonce) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Java generated header nonce: " + toHexString(merge(header, nonce)));
            LOG.debug("Size of header nonce: " + merge(header, nonce).length);
        }

        int[][] solutions = null;

        if (header != null && nonce != null) {
            // Call JNI to retrieve a solution
            solutions = this.solve(nonce, header);
        }
        return solutions;
    }

    /*
     * Mine for a single nonce
     */
    public Solution mine(IAionBlock block, byte[] nonce) {

        A0BlockHeader updateHeader = new A0BlockHeader(block.getHeader());

        byte[] inputBytes = updateHeader.getMineHash();

        BigInteger target = updateHeader.getPowBoundaryBI();

        int[][] generatedSolutions;

        // Convert byte to LE order (in place)
        toLEByteArray(nonce);

        // Get solutions for this nonce
        generatedSolutions = getSolutionsForNonce(inputBytes, nonce);

        // Increment number of solutions
        this.totalSolGenerated.addAndGet(generatedSolutions.length);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Produced " + generatedSolutions.length + " solutions");
        }

        // Add nonce and solutions, hash and check if less than target

        // Check each returned solution
        for (int[] generatedSolution : generatedSolutions) {

            // Verify if any of the solutions pass the difficulty filter, return if true.
            byte[] minimal = EquiUtils.getMinimalFromIndices(generatedSolution, cBitLen);

            byte[] validationBytes = merge(inputBytes, nonce, minimal);

            // Found a valid solution
            if (isValidBlock(validationBytes, target)) {
                return new Solution(block, nonce, minimal);
            }
        }

        return null;
    }

    /**
     * Checks if the solution meets difficulty requirements for this block.
     *
     * @param target Target under which hash must fall below
     * @return True is the solution meets target conditions; false otherwise.
     */
    private boolean isValidBlock(byte[] validationBytes, BigInteger target) {
        boolean isValid = false;

        // Default blake2b without personalization to test if hash is below
        // difficulty
        BigInteger hdrDigest = new BigInteger(1, HashUtil.h256(validationBytes));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Comparing header digest {} to target {}: ", hdrDigest, target);
        }

        if (hdrDigest.compareTo(target) < 0) {
            isValid = true;
        }
        return isValid;
    }
}
