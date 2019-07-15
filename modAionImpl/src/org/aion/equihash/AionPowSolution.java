package org.aion.equihash;

import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.IAionBlock;

/**
 * This class encapsulates a valid solution for the given block. This class allows solutions to be
 * passed between classes as needed.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class AionPowSolution implements Solution {

    private final AionBlock block;
    private final byte[] nonce;
    private final byte[] solution;

    public AionPowSolution(AionBlock block, byte[] nonce, byte[] solution) {

        this.block = block;
        this.nonce = nonce;
        this.solution = solution;
    }

    public AionBlock getBlock() {
        return block;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getSolution() {
        return solution;
    }
}
