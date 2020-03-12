package org.aion.zero.impl.blockchain;

import org.aion.mcf.blockchain.Block;

public class BlockWrapper {
    public final Block block;
    public final boolean validatedHeader;

    public BlockWrapper(Block block) {
        this.block = block;
        this.validatedHeader = false;
    }

    public BlockWrapper(Block block, boolean validHeader) {
        this.block = block;
        this.validatedHeader = validHeader;
    }
}
