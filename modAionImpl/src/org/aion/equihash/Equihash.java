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

import java.util.concurrent.atomic.AtomicLong;
import org.aion.base.util.NativeLoader;
import org.aion.crypto.HashUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.IAionBlock;
import org.slf4j.Logger;

import java.math.BigInteger;

import static java.math.BigInteger.valueOf;
import static org.aion.base.util.ByteUtil.*;
import static org.aion.base.util.Hex.toHexString;

/**
 * This class serves as the front end interface to the Tromp Equihash solver
 * accessed through JNI. This class also contains methods to verify equihash
 * solutions, either generated locally or received from peers.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class Equihash {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private int n;
    private int k;
    private int cBitLen; // Collision Bit Length used by equihash
    protected AtomicLong totalSolGenerated;

    /*
     * Load native libraries
     */
    static {
        NativeLoader.loadLibrary("sodium");
        NativeLoader.loadLibrary("equihash");
    }

    public native int[][] solve(byte[] nonce, byte[] headerBytes);

    /**
     * Create a new Equihash instance with the parameters (n,k)
     *
     * @param n
     *            Total number of bits over which to do XOR collisions
     * @param k
     *            Number of steps with which to solve.
     */
    public Equihash(int n, int k) {
        this.n = n;
        this.k = k;
        this.cBitLen = n / (k + 1);
        this.totalSolGenerated = new AtomicLong(0);
    }

    /**
     * Retrieves a set of possible solutions given the passed header and nonce
     * value Any number of solutions may be returned; the maximum number of
     * solutions observed has been 8
     *
     * @param header
     *            A 391 byte block header (header minus nonce and solutions)
     * @param nonce
     *            - A 32 byte header
     * @return An array of equihash solutions
     */
    public int[][] getSolutionsForNonce(byte[] header, byte[] nonce) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Java generated headernonce: " + toHexString(merge(header, nonce)));
            LOG.debug("Size of headernonce: " + merge(header, nonce).length);
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

        //Copy blockheader to create a local copy to modify
        A0BlockHeader updateHeader = new A0BlockHeader(block.getHeader());

        //Get timestamp
        long timeStamp = System.currentTimeMillis() / 1000;

        //Update header
        updateHeader.setTimestamp(timeStamp);

        byte[] blockHeader = updateHeader.getHeaderBytes(true);

        // Target needs to be adjusted after further exploration
        BigInteger target = valueOf(2).pow(256).divide(new BigInteger(block.getHeader().getDifficulty()));

        int[][] generatedSolutions;

        // Convert byte to LE order (in place)
        toLEByteArray(nonce);

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Mining Nonce: " + toHexString(nonce) + " Nonce Size: " + nonce.length + "  " + toHexString(nonce));
        }

        // Get solutions for this nonce
        generatedSolutions = getSolutionsForNonce(blockHeader, nonce);

        // Increment number of solutions
        this.totalSolGenerated.addAndGet(generatedSolutions.length);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Produced " + generatedSolutions.length + " solutions");
        }

        // Copy the header, add nonce and solutions, hash and check if less than
        // difficulty
        //A0BlockHeader hdr = new A0BlockHeader(block.getHeader());

        // Check each returned solution
        for (int i = 0; i < generatedSolutions.length; i++) {

            // Verify if any of the solutions pass the difficulty filter, return
            // if true.
            byte[] minimal = EquiUtils.getMinimalFromIndices(generatedSolutions[i], cBitLen);
            updateHeader.setSolution(minimal);
            updateHeader.setNonce(nonce);

            // Found a valid solution
            if (isValidBlock(updateHeader, target)) {
                return new Solution(block, nonce, minimal, timeStamp);
            }
        }

        return null;
    }

    /**
     * Checks if the solution meets difficulty requirements for this block.
     *
     * @param nc
     *            BlockHeader
     * @param target
     *            Target under which hash must fall below
     * @return True is the solution meets target conitions; false otherwise.
     */
    private boolean isValidBlock(A0BlockHeader nc, BigInteger target) {
        boolean isValid = false;

        // Default blake2b without personalization to test if hash is below
        // difficulty
        BigInteger hdrDigest = new BigInteger(1, HashUtil.h256(nc.getHeaderBytes(false)));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Header digest: " + hdrDigest);
            LOG.debug("Target:        " + target);
            LOG.debug("Difficulty:    " + new BigInteger(nc.getDifficulty()));
        }

        if (hdrDigest.compareTo(target) < 0) {
            isValid = true;
        }
        return isValid;
    }
}
