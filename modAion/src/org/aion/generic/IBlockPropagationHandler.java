package org.aion.generic;

import org.aion.base.type.IBlock;

public interface IBlockPropagationHandler<BLK extends IBlock> {
    void propagateNewBlock(BLK block);
    BlockPropagationStatus processIncomingBlock(int nodeId, String _displayId, BLK block);
}
