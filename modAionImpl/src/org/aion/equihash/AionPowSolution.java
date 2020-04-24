package org.aion.equihash;

import org.aion.zero.impl.types.MiningBlock;

/**
 * This class encapsulates a valid solution for the given block. This class allows solutions to be
 * passed between classes as needed.
 *
 * @author Ross Kitsis (ross@nuco.io)
 */
public class AionPowSolution {

    private final MiningBlock block;
    private final byte[] nonce;
    private final byte[] solution;

    public AionPowSolution(MiningBlock block, byte[] nonce, byte[] solution) {

        this.block = block;
        this.nonce = nonce;
        this.solution = solution;
    }

    public MiningBlock getBlock() {
        return block;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getSolution() {
        return solution;
    }
}
