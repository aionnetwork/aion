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
