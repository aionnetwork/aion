package org.aion.zero.impl.sync;

import java.util.Collections;
import java.util.List;
import org.aion.mcf.blockchain.Block;

/**
 * A container used to package together the blocks received from a peer with the peer's
 * identification information.
 */
final class BlocksWrapper {
    public final int nodeId;
    public final String displayId;
    public final List<Block> blocks;
    public final long firstBlockNumber;

    /**
     * A container for received blocks and peer information.
     *
     * @param nodeId the peer's node identifier
     * @param displayId the peer's display identifier
     * @param blocks the received blocks
     */
    BlocksWrapper(int nodeId, String displayId, final List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one block in the given list.");
        }
        this.nodeId = nodeId;
        this.displayId = displayId;
        this.blocks = Collections.unmodifiableList(blocks);
        this.firstBlockNumber = blocks.get(0).getNumber();
    }
}
