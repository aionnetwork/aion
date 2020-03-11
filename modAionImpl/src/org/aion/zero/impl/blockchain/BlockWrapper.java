package org.aion.zero.impl.blockchain;

import org.aion.mcf.blockchain.Block;

public class BlockWrapper {
    public final Block block;
    public final boolean validatedHeader;
    public final boolean doExistCheck;
    public final boolean reBuild;
    public final boolean flushRepo;

    public BlockWrapper(Block block) {
        this.block = block;
        this.validatedHeader = false;
        this.doExistCheck = false;
        this.reBuild = false;
        this.flushRepo = true;
    }

    public BlockWrapper(
            Block block,
            boolean validHeader,
            boolean doExistCheck,
            boolean reBuild,
            boolean flushRepo) {
        this.block = block;
        this.validatedHeader = validHeader;
        this.doExistCheck = doExistCheck;
        this.reBuild = reBuild;
        this.flushRepo = flushRepo;
    }
}
