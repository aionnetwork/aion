package org.aion.zero.impl.types;

import java.math.BigInteger;

/**
 * Wraps contextual / metadata about the block that are not part of the block itself (not associated
 * with PoW/PoS)
 */
public class BlockContext {
    public final MiningBlock block;
    public final BigInteger baseBlockReward;
    public final BigInteger transactionFee;

    public BlockContext(MiningBlock block, BigInteger baseBlockReward, BigInteger transactionFee) {
        this.block = block;
        this.baseBlockReward = baseBlockReward;
        this.transactionFee = transactionFee;
    }

    public BlockContext(BlockContext context) {
        this.block = new MiningBlock(context.block);
        this.baseBlockReward = context.baseBlockReward;
        this.transactionFee = context.transactionFee;
    }
}
