package org.aion.equihash;

import org.aion.crypto.hash.Blake2b;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import static org.aion.base.util.ByteUtil.bytesToInts;
import static org.aion.base.util.ByteUtil.intToBytesLE;
import static org.aion.base.util.ByteUtil.merge;

public class OptimizedEquiValidator {
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

    private Blake2b.Param initState;

    public OptimizedEquiValidator(int n, int k) {
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
        this.initState = this.InitialiseState();
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

        int[] indices = getIndicesFromMinimal(solution, collisionBitLength);

        byte[] hash = new byte[(n+7)/8];

        return verify(blockHeader, nonce, blake, indices, 0, hash, k);

    }


    /**
     * Generate hash based on indices and index
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
            LOG.debug("Invalid solution - indices out of order");
            return false;
        }

        byte[] hash0 = new byte[indicesHashLength];
        byte[] hash1 = new byte[indicesHashLength];

        boolean verify0 = verify(blockHeader, nonce, blake, indices, index, hash0, round-1);
        if(!verify0) {
            LOG.debug("Invalid verify0");
        }

        boolean verify1 = verify(blockHeader, nonce, blake, indices, index1, hash1, round-1);
        if(!verify1) {
            LOG.debug("Invalid verify1");
        }

        for(int i = 0; i < indicesHashLength; i++)
            hash[i] = (byte)(hash0[i] ^ hash1[i]);

        int b = (round < k ? round * collisionBitLength : n);

        for(int i = 0; i < b/8; i++) {
            if(hash[i] != 0) {
                LOG.debug("Non-zero XOR");
                return false;
            }
        }

        // Try skipping b%8 check for now

        if(round == k) {
            if(hash[indicesHashLength - 1] >> 6 > 0) {
                LOG.debug("Non-zero XOR");
                return false;
            }
        }

        return true;
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
}
