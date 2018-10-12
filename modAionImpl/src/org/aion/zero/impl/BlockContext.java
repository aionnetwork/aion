package org.aion.zero.impl;

import java.math.BigInteger;
import org.aion.zero.impl.types.AionBlock;

/**
 * Wraps contextual / metadata about the block that are not part of the block itself (not associated
 * with PoW/PoS)
 */
public class BlockContext {

    public final AionBlock block;
    public final BigInteger baseBlockReward;
    public final BigInteger transactionFee;

    public BlockContext(AionBlock block, BigInteger baseBlockReward, BigInteger transactionFee) {
        this.block = block;
        this.baseBlockReward = baseBlockReward;
        this.transactionFee = transactionFee;
    }

    public BlockContext(BlockContext context) {
        this.block = new AionBlock(context.block);
        this.baseBlockReward = context.baseBlockReward;
        this.transactionFee = context.transactionFee;
    }
}
