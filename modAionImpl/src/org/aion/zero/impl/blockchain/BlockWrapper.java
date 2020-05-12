package org.aion.zero.impl.blockchain;

import org.aion.zero.impl.types.Block;

public class BlockWrapper {
    public final Block block;
    public final boolean validatedHeader;
    public final boolean skipExistCheck;
    public final boolean reBuild;
    public final boolean skipRepoFlush;

    public BlockWrapper(Block block) {
        this.block = block;
        this.validatedHeader = false;
        this.skipExistCheck = false;
        this.reBuild = false;
        this.skipRepoFlush = false;
    }

    public BlockWrapper(
            Block block,
            boolean validHeader,
            boolean skipExistCheck,
            boolean reBuild,
            boolean skipRepoFlush) {
        this.block = block;
        this.validatedHeader = validHeader;
        this.skipExistCheck = skipExistCheck;
        this.reBuild = reBuild;
        this.skipRepoFlush = skipRepoFlush;
    }
}
