package org.aion.api.server.types;

import static org.aion.api.server.types.Fltr.Type;

import org.aion.interfaces.block.Block;
import org.aion.util.string.StringUtils;

@SuppressWarnings("rawtypes")
public class EvtBlk extends Evt {

    public final Block b;

    public EvtBlk(Block b) {
        this.b = b;
    }

    @Override
    public Type getType() {
        return Type.BLOCK;
    }

    @Override
    public String toJSON() {
        return StringUtils.toJsonHex(b.getHash());
    }
}
