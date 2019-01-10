package org.aion.api.server.types;

import org.aion.base.type.IBlockSummary;

public class FltrBlk extends Fltr {

    public FltrBlk() {
        super(Type.BLOCK);
    }

    @Override
    public boolean onBlock(IBlockSummary b) {
        add(new EvtBlk(b.getBlock()));
        return true;
    }
}
