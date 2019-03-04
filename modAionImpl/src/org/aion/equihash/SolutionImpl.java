package org.aion.equihash;

import org.aion.interfaces.block.Solution;
import org.aion.zero.types.IAionBlock;

/**
 * This class encapsulates a valid solution for the given block. This class allows solutions to be
 * passed between classes as needed.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class SolutionImpl implements Solution {

    private final IAionBlock block;
    private final byte[] nonce;
    private final byte[] solution;

    public SolutionImpl(IAionBlock block, byte[] nonce, byte[] solution) {

        this.block = block;
        this.nonce = nonce;
        this.solution = solution;
    }

    public IAionBlock getBlock() {
        return block;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getSolution() {
        return solution;
    }
}
